#!/usr/bin/python3
# script to execute horstify and to write the results into a json format

# from asyncio import subprocess
# from ast import pattern
from bdb import effective
from nis import match
import re
import sys, os
import pandas as pd
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


# Options
# -i : path to input directory
# -o : path to output directory


def main(argv):
    input_dir = ''
    output_dir = ''
    global TIMEOUT

    try:
        opts, args = getopt.getopt(argv, "hi:o:", ["input-dir=", "output-dir="])
    except getopt.GetoptError:
        print(f'execute-securify.py -i <input-dir> -o <output-dir>')
        sys.exit(2)
    for opt, arg in opts:
        if opt == '-h':
            print(f'execute-securify.py -i <input-dir> -o <output-dir>')
            sys.exit()
        elif opt in ("-i", "--input-dir"):
            input_dir = arg
        elif opt in ("-o", "--output-dir"):
            output_dir = arg

    # TODO change the path to the compiled semantics
    if not os.path.exists(output_dir):
        os.makedirs(output_dir)

    # Iterates through all files in the contract directory
    pathlist = Path(input_dir).glob('**/*.json')
    for path in pathlist:
        # because path is object not string
        path_in_str = str(path)
        # extract file name from path
        file = str(os.path.basename(path))
        contract = os.path.splitext(os.path.splitext(os.path.splitext(file)[0])[0])[0]

        # parse the .json file of the old output
        with open(path_in_str) as f:
            res_old = json.load(f)
            res_new = {}
            res_new['contract'] = os.path.splitext(os.path.splitext(res_old['contract'])[0])[
                0]  # strips the postfix from the old results
            res_new['tool'] = res_old['tool']
            res_new['version'] = res_old['version']
            res_new['timeout'] = res_old['timeout']
            res_new['timed_out'] = res_old['timed_out']
            res_new['error_occurred'] = res_old['error_occured']
            res_new['errors'] = res_old['errors']
            try:
                res_new['souffle_time'] = res_old['souffle_time']
                res_new['total_time'] = res_old['total_time']
            except:
                pass

            results = []

            # Group the matches back together
            if (res_old['results'] != []):
                df_results = pd.json_normalize(res_old['results'])
                df_results = df_results.groupby(['property', 'kind']).agg(sum)
                df_results = df_results.reset_index()
                df_rw_results = df_results[(df_results['property'] == 'RW') & (df_results['kind'] == 'Violation')]
                if df_rw_results.empty:
                    df = pd.DataFrame(columns=['property', 'kind', 'parameters'])
                    df.loc[0] = ['RW', 'Violation', []]
                    df_results = pd.concat([df_results, df])
                results_old = json.loads(df_results.to_json(orient='records'))
            else:
                res_old = []

            for res in results_old:
                result = {}
                if res['property'] == "RW":
                    result['property'] = 'RestrictedWrite'
                elif res['property'] == 'TS':
                    result['property'] = 'TimestampDepend'
                else:
                    result['property'] = res['property']
                if res['kind'] == 'Compliance':
                    result['type'] = 'Safety'
                else:
                    result['type'] = res['kind']
                matches = []
                for par in res['parameters']:
                    if par['type'] == 'PC':
                        matches.append(par['value'])
                result['matches'] = matches
                result['count'] = len(matches)
                results.append(result)

            res_new['results'] = results

        # dump the .json file with the new output
        with open(f'{output_dir}/{contract}.json', "w") as write_file:
            json.dump(res_new, write_file)


if __name__ == "__main__":
    main(sys.argv[1:])
