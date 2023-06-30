package bochum.mpi.horstify.visitors;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

public class VisitorState {

    private final int position;      // Position
    private ContractInformation.OpCode opcode;     // Opcode
    private List<BigInteger> stackInput;
    private Optional<BigInteger> stackOutput;
    private Optional<BigInteger> next;
    private Optional<BigInteger> jump;
    private List<Optional<BigInteger>> constantInformation;
    private List<BigInteger> controlDependencies;

    private boolean error = false;

    public VisitorState(int position) {
        this.position = position;
    }

    public VisitorState(BigInteger position) {
        this.position = position.intValue();
    }

    public int getPosition() {
        return position;
    }

    public ContractInformation.OpCode getOpcode() {
        return opcode;
    }

    public void setOpcode(ContractInformation.OpCode opcode) {
        this.opcode = opcode;
    }

    public List<BigInteger> getStackInput() {
        return stackInput;
    }

    public void setStackInput(List<BigInteger> stackInput) {
        this.stackInput = stackInput;
    }

    public Optional<BigInteger> getStackOutput() {
        return stackOutput;
    }

    public void setStackOutput(Optional<BigInteger> stackOutput) {
        this.stackOutput = stackOutput;
    }

    public Optional<BigInteger> getNext() {
        return next;
    }

    public void setNext(Optional<BigInteger> next) {
        this.next = next;
    }

    public Optional<BigInteger> getJump() {
        return jump;
    }

    public void setJump(Optional<BigInteger> jump) {
        this.jump = jump;
    }

    public List<Optional<BigInteger>> getConstantInformation() {
        return constantInformation;
    }

    public void setConstantInformation(List<Optional<BigInteger>> constantInformation) {
        this.constantInformation = constantInformation;
    }

    public List<BigInteger> getControlDependencies() {
        return controlDependencies;
    }

    public void setControlDependencies(List<BigInteger> controlDependencies) {
        this.controlDependencies = controlDependencies;
    }

    public void invokeError() {
        error = true;
    }
}
