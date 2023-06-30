/*
 *  Copyright 2018 Secure, Reliable, and Intelligent Systems Lab, ETH Zurich
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

/*
 * NOTICE: This file was changed by Sebastian Holler
 */


package ch.securify;

import ch.securify.decompiler.ConstantPropagation;
import ch.securify.decompiler.DecompilerFallback;
import ch.securify.decompiler.instructions.Instruction;
import ch.securify.decompiler.printer.DecompilationPrinter;
import ch.securify.utils.DevNullPrintStream;
import ch.securify.utils.Hex;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.util.Collections;
import java.util.List;


public class Securify {

    private static final PrintStream log = new DevNullPrintStream();
    private static final PrintStream progressPrinter = new DevNullPrintStream();

    /**
     * Decompile a contract binary.
     *
     * @param binary contract runtime binary
     * @return decompiled instructions
     */
    public static List<Instruction> decompileContract(byte[] binary) {
        List<Instruction> instructions;
        try {
            instructions = DecompilerFallback.decompile(binary, log);
        } catch (Exception e) {
            progressPrinter.println("  Decompilation failed.");
            throw e;
        }

        progressPrinter.println("  Propagating constants...");
        ConstantPropagation.propagate(instructions);

        log.println();
        log.println("Decompiled contract:");
        DecompilationPrinter.printInstructions(instructions, log);

        return instructions;
    }

    public static class CompilationHelpers {
        public static String sanitizeLibraries(String hexCode) {
            final String dummyAddress = "1000000000000000000000000000000000000010";
            StringBuilder sanitized = new StringBuilder();
            for (int i = 0; i < hexCode.length(); i++) {
                if (hexCode.charAt(i) == '_') {
                    sanitized.append(dummyAddress);
                    i += dummyAddress.length() - 1;
                } else {
                    sanitized.append(hexCode.charAt(i));
                }
            }
            return sanitized.toString();
        }

        public static byte[] extractBinaryFromHexFile(String filehex) throws IOException {
            File contractBinHexFile = new File(filehex);
            String hexCode = Files.readAllLines(contractBinHexFile.toPath()).get(0);
            return Hex.decode(sanitizeLibraries(hexCode));
        }

        public static byte[] extractBinaryFromFile(File contractBinHexFile)  {
            try {
                String hexCode = Files.readAllLines(contractBinHexFile.toPath()).get(0);
                return Hex.decode(sanitizeLibraries(hexCode));
            }
            catch (IOException e) {
                throw new RuntimeException(e);
            }
        }


    }
}
