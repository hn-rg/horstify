#!/usr/bin/python3
# script to execute horstify and to write the results into a json format

# from asyncio import subprocess
# from ast import pattern
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

ROOT_DICT = f"/tmp/securify/"
PROFILE_SECURIFY = "semantics/profile_securify.out"


def parseSecurifyPattern(patternResult, property, res):
    result = {}
    result['property'] = property  # TODO: maybe we rather want to strip that part from the HoRStify results ?
    result['type'] = 'Safety'
    i = 0
    matches = []
    for safe_match in patternResult['safe']:
        matches.append(safe_match)
        i += 1
    result['matches'] = matches
    result['count'] = i
    res['results'].append(result)

    result = {}
    result['property'] = property
    result['type'] = 'Violation'
    i = 0
    matches = []
    for safe_match in patternResult['violations']:
        matches.append(safe_match)
        i += 1
    result['matches'] = matches
    result['count'] = i
    res['results'].append(result)
    return


def securify(file_stripped, options):
    res = {
        "tool": 'securify',
        "version": '0',
        "contract": file_stripped,
        "timeout": TIMEOUT,
        "timed_out": True,
        "error_occurred": False,
        "results": [],
        "errors": []
    }

    start_time = datetime.datetime.now()
    print(f"securify {options}")
    proc = subprocess.Popen(["securify", f"{options}"], stdout=PIPE, stderr=PIPE,
                            preexec_fn=os.setsid)

    try:
        stream, errs = proc.communicate(timeout=TIMEOUT)
    except TimeoutExpired:
        os.killpg(os.getpgid(proc.pid), signal.SIGTERM)
        return res
    end_time = datetime.datetime.now()

    # If the execution made it here then there is no timeout
    res['timed_out'] = False

    # Check whether there were errors produced during the execution
    if (errs != b''):
        res['errors'].append(errs.decode('utf-8'))
        res['error_occurred'] = True
        return res

    # parses securify result into a json object
    str = stream.decode('utf-8')

    try:
        securify_result = json.loads(str)
    # if the result could not be parsed into a json write the corresponding error message to the object
    except BaseException as err:
        res['errors'].append(format(err))
        res['error_occurred'] = True
        return res

    exec_time = int((end_time - start_time).total_seconds() * 1000)

    # brings Securify output into the required format
    if securify_result['finished'] == True:
        res['timed_out'] = False

    res['total_time'] = exec_time
    res['souffle_time'] = securify_result['souffle_time']

    # TODO check correct names in Securify
    p_results = securify_result['patternResults']

    # TODO adapt names
    if 'TimestampDependency' in p_results:
        ts = p_results['TimestampDependency']
        parseSecurifyPattern(ts, 'TimestampDepend', res)
    if 'UnrestrictedWrite' in p_results != None:
        rw = p_results['UnrestrictedWrite']
        parseSecurifyPattern(rw, 'RestrictedWrite', res)
    if 'MissingInputValidation' in p_results != None:
        va = p_results['MissingInputValidation']
        parseSecurifyPattern(va, 'VA', res)
    if 'UnhandledException' in p_results:
        he = p_results['UnhandledException']
        parseSecurifyPattern(he, 'HE', res)
    if 'TODAmount' in p_results:
        tod = p_results['TODAmount']
        parseSecurifyPattern(tod, 'TODAmount', res)
    return res


# Options
# -c : path to the contract directory 
# -t : timeout (in s)
# -r : path to result directory
# -i : ignore list (.txt file)
# -a : allow list (.txt file)


def main(argv):
    contract_dir = ''
    result_dir = ''
    global TIMEOUT
    ignore_list = ''
    allow_list = ''

    try:
        opts, args = getopt.getopt(argv, "hc:t:r:i:a:", ["contracts=", "timeout=", "results=", "ignore=", "allow="])
    except getopt.GetoptError:
        print(f'execute-securify.py -c <contract-dir> -t <timeout> -r <result-dir> -i <ignore-list> -a <allow-list>')
        sys.exit(2)
    for opt, arg in opts:
        if opt == '-h':
            print(
                f'execute-securify.py -c <contract-dir> -t <timeout> -r <result-dir> -i <ignore-list> -a <allow-list>')
            sys.exit()
        elif opt in ("-c", "--contracts"):
            contract_dir = arg
        elif opt in ("-t", "--timeout"):
            TIMEOUT = int(arg)
        elif opt in ("-r", "--results"):
            result_dir = arg
        elif opt in ("-i", "--ignore"):
            ignore_list = arg
        elif opt in ("-a", "--allow"):
            allow_list = arg

    # read contract addresses from file
    if ignore_list != '':
        f_ignore = open(ignore_list)
        content = f_ignore.read()
        ignore = content.split("\n")
    else:
        ignore = []

    # read contract addresses from file
    if allow_list != '':
        f_allow = open(allow_list)
        content = f_allow.read()
        allow = content.split("\n")
        allow_exists = True
    else:
        allow = []
        allow_exists = False


    # Iterates through all files in the contract directory
    pathlist = Path(contract_dir).glob('**/*.bin.hex')
    for path in pathlist:
        # because path is object not string
        path_in_str = str(path)
        # extract file name from path
        file = str(os.path.basename(path))
        file_stripped = os.path.splitext(os.path.splitext(file)[0])[0]

        if allow_exists and (not file_stripped in allow):
            print(f'{file_stripped} not allowed')
            continue

        if file_stripped in ignore:
            print(f'Ignore {file_stripped}')
            continue

        # execute securify

        # Assumption: Securify has a flag to output a json result TODO this option still needs to be enabled here
        securify_result = securify(file_stripped, f'--profiler "{ROOT_DICT}{PROFILE_SECURIFY}" --magic {path_in_str}')

        # write resulting object into a .json file in the result directory
        with open(f'{result_dir}/{file}.json', "w") as write_file:
            json.dump(securify_result, write_file)


if __name__ == "__main__":
    main(sys.argv[1:])
