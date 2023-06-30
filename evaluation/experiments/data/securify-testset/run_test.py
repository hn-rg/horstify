import os
import pandas as pd
from contextlib import contextmanager

patterns_violation  = ["UnrestrictedWrite", "UnhandledException"]
patterns_safety     = ["TODReceiver", "TODAmount"]
total_constracts = 0
failed_tests = 0

def get_files():
    with open("contracts.txt") as f:
        lines = [line.rstrip('\n') for line in f]
    return lines

def count(line):
    file = pd.read_json(line + ".json")

    rest_write = 0
    #handled_exp = 0
    tod_safe = 0

    for key, value in file.items():
        results = value["results"]
        rest_write += len(results["UnrestrictedWrite"]["violations"]) + len(results["UnrestrictedWrite"]["conflicts"]) 
        #handled_exp += len(results["UnhandledException"]["violations"]) + len(results["UnhandledException"]["conflicts"])
        #tod_safe += len(results["TODReceiver"]["violations"]) + len(results["TODReceiver"]["conflicts"]) 
        #tod_safe += len(results["TODAmount"]["violations"]) + len(results["TODAmount"]["conflicts"]) 
        
    #list = [rest_write, handled_exp, tod_safe]
    res = [rest_write]

    return res




def execute(file):
    stream = os.popen('java -Dorg.apache.logging.log4j.simplelog.StatusLogger.level=OFF -classpath ../../../../../target/horstify-0.2-shaded.jar uds.horstify.IRHorstCompiler --decompile ' + file + ".bin.hex")
    results = []

    line = stream.readline().rstrip('\n');
    while line:
        line = line.strip()
        if line.isdigit():
            results.append(int(line))
        line = stream.readline()

    final = []
    #for i in [0,3,5]:
    if len(results) > 0:
        for i in [0]:
            final.append(results[i])
    return final


def compare(result, expected):
    if list(result) ==  list(expected):
        print(" Success\n")
        return 0

    if int(result[0])!= int(expected[0]):
        print("Restricted Write: Got " + str(result[0]) + " but expected " + str(str(expected[0])))
    #if int(result[1])> int(expected[1]):
    #    print("UnhandledException: Got " + str(result[1]) + " but expected " + str(expected[1]))
    #if int(result[2])!= int(expected[2]):
    #    print("TOD: Got " + str(result[2]) + " but expected " + str(expected[2]))
    return 1


for file in get_files():
    print("Test " + file)
    expected = count(file)
    result = execute(file)
    failed_tests += compare(result, expected)
    total_constracts += 1
    
print(str(failed_tests) +" failed from a total of " + str(total_constracts))


