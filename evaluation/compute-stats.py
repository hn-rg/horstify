#!/usr/bin/python3
# Script to compute statistics over analysis results
from wsgiref import headers
import pandas as pd
import numpy as np
from bdb import effective
from nis import match
import re
import sys, os
import subprocess
from subprocess import Popen, PIPE, TimeoutExpired
from time import time
from contextlib import contextmanager
import datetime
import io
import signal
import json
import getopt
from pathlib import Path

MATCHES = True
COUNT = True


def parseDataDir(data_dir):
    pathlist = Path(data_dir).glob('**/*.json')

    df_contracts = pd.DataFrame()
    df_results = pd.DataFrame()
    df_matches = pd.DataFrame()

    for path in pathlist:
        # because path is object not string
        path_in_str = str(path)
        # extract file name from path
        file = str(os.path.basename(path))

        # read the contract data
        with open(path_in_str, 'r') as f:
            data = json.loads(f.read())  # read data from json
        df_per_contract_data = pd.json_normalize(data, errors='ignore')
        # drop the concrete results
        df_per_contract_data = df_per_contract_data.drop(['results'], axis=1)

        df_contracts = pd.concat([df_contracts, df_per_contract_data], join='outer')

        # Flatten data
        df_results_data = pd.json_normalize(
            data,
            record_path=['results'],
            meta=['tool', 'version', 'contract', 'total_time', 'souffle_time', 'timeout', 'timed_out',
                  'error_occurred'],
            errors='ignore'
        )
        df_results = pd.concat([df_results, df_results_data])  # TODO Merge all results with same patterns!

        # Flatten data
        df_matches_data = pd.json_normalize(
            data,
            record_path=['results', 'matches'],
            meta=['tool', 'version', 'contract', 'total_time', 'souffle_time', 'timeout', 'timed_out', 'error_occurred',
                  ['matches', 'type'], ['matches', 'property'], ['matches', 'pattern']],
            errors='ignore'
        )
        df_matches = pd.concat([df_matches, df_matches_data])

    # problem: we loose results without patterns!

    # matches are the same as before, it is sufficient to just drop 'pattern'
    df_matches = df_matches.rename(
        columns={0: 'pc', 'matches.type': 'type', 'matches.property': 'property', 'matches.pattern': 'pattern'}).drop(
        'pattern', axis=1)
    # to obtain the results one needs to reconstruct the matches
    df_results_2 = df_matches.groupby(
        ['tool', 'version', 'contract', 'type', 'property', 'total_time', 'souffle_time', 'timeout', 'timed_out',
         'error_occurred'], as_index=False).agg(matches=('pc', list), match_count=('pc',
                                                                                   np.size))  # aggregates all matches (pcs) in a list for one property (column matches) and counts the number of matches (match_count)

    # rename new results table 
    df_results_2 = df_results_2.rename(
        columns=lambda x: x if ((x == 'contract') or (x == 'type') or (x == 'property')) else x + '_1')

    # compute the left join on original results table to ensure that all result entries are kept
    df_results = df_results.merge(df_results_2, how='outer', indicator=True, on=['contract', 'type', 'property'])
    df_results = df_results[(df_results['_merge'] == 'left_only') | (df_results['_merge'] == 'both')].drop('_merge',
                                                                                                           axis=1)
    df_results = df_results.drop(
        ['tool_1', 'version_1', 'total_time_1', 'souffle_time_1', 'timeout_1', 'timed_out_1', 'error_occurred_1',
         'matches', 'count'], axis=1)
    df_results.loc[df_results['matches_1'].isnull(), ['matches_1']] = df_results.loc[
        df_results['matches_1'].isnull(), 'matches_1'].apply(
        lambda x: [])  # replace non-existent matches with the empty list
    df_results = df_results.rename(columns={'matches_1': 'matches'})
    df_results = df_results.drop_duplicates(subset=['contract', 'type', 'property', 'tool', 'version'])

    df_results.loc[df_results['match_count_1'].isnull(), ['match_count_1']] = df_results.loc[
        df_results['match_count_1'].isnull(), 'match_count_1'].apply(lambda x: 0)  # replace non-existent counts with 0
    df_results = df_results.rename(columns={'match_count_1': 'count'})

    # df_matches =  df_matches.rename(columns={ 0: 'pc', 'matches.type': 'type', 'matches.property': 'property'})

    return df_contracts, df_results, df_matches


def statsPerTool(df_contracts):
    total_count = len(df_contracts)

    error_count = len(df_contracts[df_contracts['error_occurred'] == True])

    timeout_count = len(df_contracts[df_contracts['timed_out'] == True])

    df_contracts_succ = df_contracts[(df_contracts['timed_out'] == False) & (df_contracts['error_occurred'] == False)]

    avg_total_time = df_contracts_succ[
        'total_time'].mean()  # compare these times on the contracts that did not timeout or had an error

    avg_souffle_time = df_contracts_succ['souffle_time'].mean()

    tool = df_contracts.iat[0, 0]

    contract_data = [[tool, total_count, error_count, timeout_count, avg_total_time, avg_souffle_time]]

    df_overview = pd.DataFrame(contract_data,
                               columns=['Tool', 'Total Number of Contracts', 'Errors', 'Timeouts', 'Avg Total Time',
                                        'Avg Souffle Time'])

    return df_overview


# Arguments
# -d repository (or repositories divided by ':') with the data to be analyzed/compared
# -o result directory

def main(argv):
    data_dirs = []
    out_dir = ''

    try:
        opts, args = getopt.getopt(argv, "hd:o:", ["data=", "out="])
    except getopt.GetoptError:
        print(f'compute-stats.py -d <data-dir1> : [<data-dir2>]')
        sys.exit(2)
    for opt, arg in opts:
        if opt == '-h':
            print(f'compute-stats.py -d <data-dir1> : [<data-dir2>]')
            sys.exit()
        elif opt in (
        "-d", "--data"):  # if only one file is given, individual files are given, otherwise the files are compared
            data_dirs = arg.split(':')
        elif opt in ("-o", "--out-dir"):
            out_dir = arg

    # create out_dir if it does not exist
    if not os.path.exists(out_dir):
        os.makedirs(out_dir)

    # distinguish cases of 1 or multiple data directories to compare
    if (len(data_dirs) == 1):
        # compute the stats for the contracts and write it to the result directory
        df_contracts, df_results, df_matches = parseDataDir(data_dirs[0])
        df_overview = statsPerTool(df_contracts)
        print(df_overview)
        df_overview.to_csv(f'{out_dir}overview.csv', sep=',', index=False, header=True)
    elif (len(data_dirs) == 2):
        print('Two data sets to compare!')

        df_contracts1, df_results1, df_matches1 = parseDataDir(data_dirs[0])
        df_contracts2, df_results2, df_matches2 = parseDataDir(data_dirs[1])

        # --------- Produces statistics for both tools on the set of contracts that both tried to solve 

        # Compute number of contracts on which both have been running

        # rename columns
        df_contracts1_renamed = df_contracts1.rename(columns=lambda x: x if (x == 'contract') else x + '_1')
        df_contracts2_renamed = df_contracts2.rename(columns=lambda x: x if (x == 'contract') else x + '_2')

        # inner join on 'contract' to compute the set where both tables have results for contracts
        df_contracts_both = pd.merge(df_contracts1_renamed, df_contracts2_renamed, on='contract')

        # restrict to those contracts that were tried by both
        df_contracts1 = pd.merge(df_contracts1, df_contracts_both, on='contract')[
            ['contract', 'tool', 'version', 'total_time', 'souffle_time', 'timeout', 'timed_out', 'error_occurred',
             'errors']]
        df_contracts2 = pd.merge(df_contracts2, df_contracts_both, on='contract')[
            ['contract', 'tool', 'version', 'total_time', 'souffle_time', 'timeout', 'timed_out', 'error_occurred',
             'errors']]

        # Compute overview for contracts that they both tried
        df_overview1 = statsPerTool(df_contracts1)
        df_overview2 = statsPerTool(df_contracts2)

        # Compute overview for contracts on which they both succeeded

        df_contracts_succ = df_contracts_both[
            (df_contracts_both['timed_out_1'] == False) & (df_contracts_both['timed_out_2'] == False) & (
                        df_contracts_both['error_occurred_1'] == False) & (
                        df_contracts_both['error_occurred_2'] == False)]

        # Compute number of contracts on which both have been succeding

        count_both_tried = len(df_contracts_both)
        count_both_succeded = len(df_contracts_succ)

        df_contracts1_succ = pd.merge(df_contracts1, df_contracts_succ, on='contract')[
            ['contract', 'tool', 'version', 'total_time', 'souffle_time', 'timeout', 'timed_out', 'error_occurred',
             'errors']]
        df_contracts2_succ = pd.merge(df_contracts2, df_contracts_succ, on='contract')[
            ['contract', 'tool', 'version', 'total_time', 'souffle_time', 'timeout', 'timed_out', 'error_occurred',
             'errors']]

        df_overview_succ1 = statsPerTool(df_contracts1_succ)
        df_overview_succ2 = statsPerTool(df_contracts2_succ)

        df_overview1['Avg Total Time(Intersection)'] = df_overview_succ1['Avg Total Time']
        df_overview1['Avg Souffle Time(Intersection)'] = df_overview_succ1['Avg Souffle Time']
        df_overview2['Avg Total Time(Intersection)'] = df_overview_succ2['Avg Total Time']
        df_overview2['Avg Souffle Time(Intersection)'] = df_overview_succ2['Avg Souffle Time']

        df_overview_joint = pd.concat([df_overview1, df_overview2])
        df_overview_joint.to_csv(f'{out_dir}overview_joint.csv', sep=',', index=False, header=True)

        compare_data = [[df_contracts1['tool'][0], df_contracts2['tool'][0], count_both_tried, count_both_succeded]]

        df_overview_compare = pd.DataFrame(compare_data, columns=['Tool1', 'Tool2', 'Total Number of Contracts',
                                                                  'Contracts Succesfully solved by both'])

        if COUNT == True:

            # Remove all results that we are not interested in

            df_results1 = df_results1[
                ((df_results1['property'] == 'RestrictedWrite') & (df_results1['type'] == 'Violation')) | (
                            (df_results1['property'] == 'TimestampDepend') & (df_results1['type'] == 'Safety'))]
            df_results2 = df_results2[
                ((df_results2['property'] == 'RestrictedWrite') & (df_results2['type'] == 'Violation')) | (
                            (df_results2['property'] == 'TimestampDepend') & (df_results2['type'] == 'Safety'))]

            # restrict the results table to those contracts where both succeeded 
            df_results1 = df_results1.merge(df_contracts_succ, on='contract', how='inner')[
                ['tool', 'contract', 'property', 'type', 'matches',
                 'count']]  # TODO: here probably the CREATE count gets lost because for the same property there could be different counts. But there could also be different mataches?! Why isn't that a problem?!
            df_results2 = df_results2.merge(df_contracts_succ, on='contract', how='inner')[
                ['tool', 'contract', 'property', 'type', 'matches', 'count']]

            # rename to make unique
            df_results1_renamed = df_results1.rename(
                columns=lambda x: x if ((x == 'contract') or (x == 'type') or (x == 'property')) else x + '_1')
            df_results2_renamed = df_results2.rename(
                columns=lambda x: x if ((x == 'contract') or (x == 'type') or (x == 'property')) else x + '_2')

            # computes a combined table
            df_results_merged = df_results1_renamed.merge(df_results2_renamed, how='outer', indicator=True,
                                                          on=['contract', 'type', 'property'])

            # compute mismatches (sanity check): there should be none (they should only differ in the count)

            mismatching_results_left = df_results_merged[df_results_merged['_merge'] == 'left_only'].drop('_merge',
                                                                                                          axis=1)
            if (mismatching_results_left.empty == False):
                raise Exception('Results in HoRStify Data Missing')

            mismatching_results_right = df_results_merged[df_results_merged['_merge'] == 'right_only'].drop('_merge',
                                                                                                            axis=1)
            if (mismatching_results_right.empty == False):
                raise Exception('Results in Securify Data Missing')

            # Compute differences in count 

            # good mismatches Securify(1) has more matches (more independencies) than Horstify(2)
            df_results_merged = df_results_merged[df_results_merged['_merge'] == 'both'].drop('_merge',
                                                                                              axis=1)  # basically we are back to the inner join

            df_count_diff_good = df_results_merged[df_results_merged['count_1'] > df_results_merged['count_2']]
            df_count_diff_bad = df_results_merged[df_results_merged['count_1'] < df_results_merged['count_2']]

            count_diff_good = len(df_count_diff_good)
            count_diff_bad = len(df_count_diff_bad)

            df_overview_compare['Good Mismatches(Count)'] = [count_diff_good]
            df_overview_compare['Bad Mismatches(Count)'] = [count_diff_bad]

            df_count_diff_good.to_csv(f'{out_dir}mismatches_good_count.csv', sep=',', index=False, header=True)
            df_count_diff_bad.to_csv(f'{out_dir}mismatches_bad_count.csv', sep=',', index=False, header=True)

            # Compute binary mismatches (contracts labeled safe vs. unsafe)

            df_binary_diff_good = df_results_merged[((df_results_merged['property'] == 'RestrictedWrite') & (
                        df_results_merged['count_1'] > 0) & (df_results_merged['count_2'] == 0)) | (
                                                                (df_results_merged['property'] == 'TimestampDepend') & (
                                                                    df_results_merged['count_1'] > df_results_merged[
                                                                'count_2']))]
            df_binary_diff_bad = df_results_merged[((df_results_merged['property'] == 'RestrictedWrite') & (
                        df_results_merged['count_2'] > 0) & (df_results_merged['count_1'] == 0)) | (
                                                               (df_results_merged['property'] == 'TimestampDepend') & (
                                                                   df_results_merged['count_2'] > df_results_merged[
                                                               'count_1']))]

            df_test = df_results_merged[(df_results_merged['property'] == 'TimestampDepend') & (
                        df_results_merged['count_2'] > df_results_merged['count_1'])]

            print(df_test)

            binary_diff_good = len(df_binary_diff_good)
            binary_diff_bad = len(df_binary_diff_bad)

            df_overview_compare['Good Mismatches(Binary)'] = [binary_diff_good]
            df_overview_compare['Bad Mismatches(Binary)'] = [binary_diff_bad]

            df_binary_diff_good.to_csv(f'{out_dir}mismatches_good_binary.csv', sep=',', index=False, header=True)
            df_binary_diff_bad.to_csv(f'{out_dir}mismatches_bad_binary.csv', sep=',', index=False, header=True)

            # -------- Write tables with mismatches to output directory

        if MATCHES == True:
            # Remove all results that we are not interested in
            df_matches1 = df_matches1[
                (df_matches1['property'] == 'RestrictedWrite') | (df_matches1['property'] == 'TimestampDepend')]
            df_matches1 = df_matches1[
                (df_matches1['property'] != 'RestrictedWrite') | (df_matches1['type'] == 'Violation')]

            df_matches2 = df_matches2[
                (df_matches2['property'] == 'RestrictedWrite') | (df_matches2['property'] == 'TimestampDepend')]
            df_matches2 = df_matches2[
                (df_matches2['property'] != 'RestrictedWrite') | (df_matches2['type'] == 'Violation')]

            # restrict the results table to those contracts where both succeeded
            df_matches1 = df_matches1.merge(df_contracts_succ, on='contract', how='inner')[
                ['pc', 'tool', 'version', 'contract', 'type', 'property']]
            df_matches2 = df_matches2.merge(df_contracts_succ, on='contract', how='inner')[
                ['pc', 'tool', 'version', 'contract', 'type', 'property']]

            df_matches1_renamed = df_matches1.rename(columns=lambda x: x if (
                        (x == 'contract') or (x == 'pc') or (x == 'type') or (x == 'property')) else x + '_1')
            df_matches2_renamed = df_matches2.rename(columns=lambda x: x if (
                        (x == 'contract') or (x == 'pc') or (x == 'type') or (x == 'property')) else x + '_2')

            # compute those matches in 1 where there is no corresponding match in 2 (anti-semi join?) (PC, contract matches.type, matches.property)

            # computes a combined table 
            df_matches_merged = df_matches1_renamed.merge(df_matches2_renamed, how='outer', indicator=True,
                                                          on=['pc', 'contract', 'type', 'property'])

            # If 1 = Securify and 2 = Horstify then these are the good mismatches (matches -> independence), so Securify claimed something to be independent where we find a dependency

            df_mismatches_left = df_matches_merged[df_matches_merged['_merge'] == 'left_only'].drop(
                ['_merge', 'tool_2', 'version_2'], axis=1)
            # mismatches_left = [~(df_matches_merged._merge == 'left_only')].drop('_merge', axis = 1)  # we keep only those that were only matched on the left

            # If 1 = Securify and 2 = Horstify then these are the bad mismatches (matches -> independence), so we claimed something to be independent where Securify founf a dependency
            df_mismatches_right = df_matches_merged[df_matches_merged['_merge'] == 'right_only'].drop(
                ['_merge', 'tool_1', 'version_1'], axis=1)
            # mismatches_right = [~(df_matches_merged._merge == 'right_only')].drop('_merge', axis = 1) # we keep only those that were only matched on the right

            mismatches_left_count = len(df_mismatches_left)
            mismatches_right_count = len(df_mismatches_right)

            df_overview_compare['Good Mismatches(PC)'] = [mismatches_left_count]
            df_overview_compare['Bad Mismatches(PC)'] = [mismatches_right_count]

            # prepare overview table for good/bad mismatches (based on contracts)
            # idea: join with results table contract tool_1 version_1 matches_1 tool_2 version_2 matches_2 bad_mismatches(tool_2)

            # group frame per contract, type and property
            df_good_mismatches_per_contract = df_mismatches_left.groupby(['contract', 'type', 'property']).agg(
                mismatches=('pc', list))
            df_good_mismatches_overview = df_good_mismatches_per_contract.merge(df_results_merged, how='inner',
                                                                                on=['contract', 'type', 'property'])

            df_bad_mismatches_per_contract = df_mismatches_right.groupby(['contract', 'type', 'property']).agg(
                mismatches=('pc', list))
            df_bad_mismatches_overview = df_bad_mismatches_per_contract.merge(df_results_merged, how='inner',
                                                                              on=['contract', 'type', 'property'])
            print(df_bad_mismatches_per_contract)

            print(df_overview_joint)
            print(df_overview_compare)

            df_overview_compare.to_csv(f'{out_dir}overview_compare.csv', sep=',', index=False, header=True)
            df_mismatches_left.to_csv(f'{out_dir}mismatches_good_pc.csv', sep=',', index=False, header=True)
            df_mismatches_right.to_csv(f'{out_dir}mismatches_bad_pc.csv', sep=',', index=False, header=True)
            df_good_mismatches_overview.to_csv(f'{out_dir}mismatches_good_pc_overview.csv', sep=',', index=False,
                                               header=True)
            df_bad_mismatches_overview.to_csv(f'{out_dir}mismatches_bad_pc_overview.csv', sep=',', index=False,
                                              header=True)

    else:
        print('Wrong number of arguments')
        sys.exit()

    # Iterates through all files in the contract directory

    # todo: comparative table (numbers on which both tools ran, numbers on which both tools succeeded)

    # todo: table "good_mismatches"

    # todo: table "bad_mismatches"


if __name__ == "__main__":
    main(sys.argv[1:])
