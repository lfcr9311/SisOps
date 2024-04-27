public enum Opcode {
    DATA, ___, // se memoria nesta posicao tem um dado, usa DATA, se nao usada ee NULO ___
    JMP, JMPI, JMPIG, JMPIL, JMPIE, // desvios e parada
    JMPIM, JMPIGM, JMPILM, JMPIEM, STOP,
    JMPIGK, JMPILK, JMPIEK, JMPIGT,
    ADDI, SUBI, ADD, SUB, MULT, // matematicos
    LDI, LDD, STD, LDX, STX, MOVE, // movimentacao
    SYSCALL // chamada de sistema
}