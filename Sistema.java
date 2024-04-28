// PUCRS - Escola Politécnica - Sistemas Operacionais
// Prof. Fernando Dotti
// Código fornecido como parte da solução do projeto de Sistemas Operacionais
//
// VM
//    HW = memória, cpu
//    SW = tratamento int e chamada de sistema
// Funcionalidades de carga, execução e dump de memória

import java.util.*;

public class Sistema {

	// -------------------------------------------------------------------------------------------------------
	// --------------------- H A R D W A R E - definicoes de HW
	// ----------------------------------------------

	// -------------------------------------------------------------------------------------------------------
	// --------------------- M E M O R I A - definicoes de palavra de memoria,
	// memória ----------------------

	public class Memory {
		private int tamMemoria;
		private int tamPagina;
		private int numeroFrames;
		private boolean[] listaFrames;
		public Word[] memoriaFisica;
	
		public Memory(int size, int tamPag) {
			this.tamMemoria = size;
			this.tamPagina = tamPag;
			this.numeroFrames = size / tamPagina;
			this.listaFrames = new boolean[numeroFrames];
	
			for (int i = 0; i < listaFrames.length; i++) {
				listaFrames[i] = true;
			}
	
			memoriaFisica = new Word[tamMemoria];
			for (int i = 0; i < tamMemoria; i++) {
				memoriaFisica[i] = new Word(Opcode.___, -1, -1, -1);
			}
		}
	
		public int traduzEndereco(int enderecoLogico, int[] tabelaPaginas) {
			int numeroPagina = enderecoLogico / tamPagina;
			if (numeroPagina >= tabelaPaginas.length || numeroPagina < 0) {
				System.err.println("Erro: Número de página inválido");
			}
			int offset = enderecoLogico % tamPagina;
			int frameIndex = tabelaPaginas[numeroPagina];
			int enderecoFisico = frameIndex * tamPagina + offset;
			return enderecoFisico;
		}
		
		public int[] aloca(int numPalavras) {
			int numFramesNecessarios = (numPalavras / tamPagina) + 1;
			int[] tabelaPaginas = new int[numFramesNecessarios];
			int framesAlocados = 0;
			
			for (int i = 0; (i < numeroFrames) && (framesAlocados != numFramesNecessarios); i++) {
				if (listaFrames[i]) {
					tabelaPaginas[framesAlocados] = i;
					listaFrames[i] = false;
					framesAlocados++;
				}
			}
			
			if (framesAlocados != numFramesNecessarios) {
				System.out.println("Erro: Memória insuficiente");
				for (int i = 0; i < framesAlocados; i++) {
					listaFrames[tabelaPaginas[i]] = true;
				}
				return null;
			}
			return tabelaPaginas;
		}
	
		
		public void desaloca(int[] tabelaPaginas) {
			for (int i = 0; i< tabelaPaginas.length; i++) {
				listaFrames[i] = true;
			}
		}
	
		
		public void dump(Word w) {
			System.out.print("[ ");
			System.out.print(w.opc);
			System.out.print(", ");
			System.out.print(w.r1);
			System.out.print(", ");
			System.out.print(w.r2);
			System.out.print(", ");
			System.out.print(w.p);
			System.out.println(" ]");
		}
		
		public void dump(int ini, int fim) {
			for (int i = ini; i < fim; i++) {
				System.out.print(i);
				System.out.print(":  ");
				dump(memoriaFisica[i]);
			}
		}
	}

	public class ProcessManager {
		private Memory memoria;
		private int id;
		private ProcessControlBlock[] pcb;
		private ProcessControlBlock status;
	
		public ProcessManager(Memory mem) {
			id = 0;
			this.memoria = mem;
			pcb = new ProcessControlBlock[mem.numeroFrames];
		}
	
		
		public boolean criaProcesso(Word[] program) {
			int[] tabelaPaginas = memoria.aloca(program.length);
			if (tabelaPaginas != null) {
				ProcessControlBlock newpcb = new ProcessControlBlock(id);
				newpcb.tabelaPaginas = tabelaPaginas;
				newpcb.processState = true;
				newpcb.running = false;
				this.pcb[id] = newpcb;
				id++;
			} else {
				return false;
			}
			// Carrega o programa na memoria
			for (int i = 0; i < program.length; i++) {
				int enderecoFisico = memoria.traduzEndereco(i, tabelaPaginas);
				memoria.memoriaFisica[enderecoFisico].opc = program[i].opc;
				memoria.memoriaFisica[enderecoFisico].r1 = program[i].r1;
				memoria.memoriaFisica[enderecoFisico].r2 = program[i].r2;
				memoria.memoriaFisica[enderecoFisico].p = program[i].p;
			}
			return true;
		}
	
		public boolean desalocaProcesso(int id) {
			if (pcb[id] == null) {
				return false; 
			} else {
				memoria.desaloca(pcb[id].tabelaPaginas);
				pcb[id] = null;
				return true; 
			}
		}
	
		public boolean executaProcesso(int id) {
			if (pcb[id] != null) {
				pcb[id].running = true;
				status = pcb[id];
				while ((pcb[id] != null) && (pcb[id].running)) {
					
					vm.cpu.setContext(0, memoria.tamMemoria - 1, pcb[id].pc);
					vm.cpu.run();
				}
				return true;
			}
			return false; 
		}
	}
	

	public class ProcessControlBlock {
		public int[] tabelaPaginas;
		public boolean processState;
		public boolean running;
		public int pc;
		public int id;

		public ProcessControlBlock(int id) {
			tabelaPaginas = null;
			processState = false;
			running = false;
			pc = 0;
			this.id = id;
		}
	}

	// -------------------------------------------------------------------------------------------------------
	// --------------------- C P U - definicoes da CPU
	// -----------------------------------------------------

	public class CPU {
		private int maxInt; // valores maximo e minimo para inteiros nesta cpu
		private int minInt;
		private boolean trace;
		// característica do processador: contexto da CPU ...
		private int pc; // ... composto de program counter,
		private Word ir; // instruction register,
		private int[] reg; // registradores da CPU
		private Interrupts irpt; // durante instrucao, interrupcao pode ser sinalizada
		private int base; // base e limite de acesso na memoria
		private int limite; // por enquanto toda memoria pode ser acessada pelo processo rodando
		// ATE AQUI: contexto da CPU - tudo que precisa sobre o estado de um processo
		// para executa-lo
		// nas proximas versoes isto pode modificar

		private Memory mem; // mem tem funcoes de dump e o array m de memória 'fisica'
		private Word[] m; // CPU acessa MEMORIA, guarda referencia a 'm'. m nao muda. semre será um array
		// de palavras

		private InterruptHandling ih; // significa desvio para rotinas de tratamento de Int - se int ligada, desvia
		private SysCallHandling sysCall; // significa desvio para tratamento de chamadas de sistema - SYSCALL
		private boolean debug; // se true entao mostra cada instrucao em execucao
		public ProcessManager pm;

		public CPU(Memory _mem, InterruptHandling _ih, SysCallHandling _sysCall, boolean _debug) { // ref a MEMORIA e
	
			maxInt = 32767; // capacidade de representacao modelada
			minInt = -32767; // se exceder deve gerar interrupcao de overflow
			mem = _mem; // usa mem para acessar funcoes auxiliares (dump)
			m = mem.memoriaFisica; // usa o atributo 'm' para acessar a memoria.
			reg = new int[10]; // aloca o espaço dos registradores - regs 8 e 9 usados somente para IO
			ih = _ih; // aponta para rotinas de tratamento de int
			sysCall = _sysCall; // aponta para rotinas de tratamento de chamadas de sistema
			debug = _debug; // se true, print da instrucao em execucao
			pm = new ProcessManager(mem);
			trace = true;
		}

		private boolean legal(int e) { // todo acesso a memoria tem que ser verificado
			if (e >= 0 && e < mem.tamMemoria) {
				return true;
			} else {
				irpt = Interrupts.intEnderecoInvalido; // Set the interrupt type if illegal
				return false;
			}
		}

		private boolean testOverflow(int v) {
			if ((v < minInt) || (v > maxInt)) {
				irpt = Interrupts.intOverflow;
				return false;
			}
			return true;
		}

		public void setContext(int _base, int _limite, int _pc) { // no futuro esta funcao vai ter que ser
			base = _base; // expandida para setar todo contexto de execucao,
			limite = _limite; // agora, setamos somente os registradores base,
			pc = _pc; // limite e pc (deve ser zero nesta versao)
			irpt = Interrupts.noInterrupt; // reset da interrupcao registrada
		}

		public void run() { // execucao da CPU supoe que o contexto da CPU, vide acima, esta devidamente
			// setado
			while (true) { // ciclo de instrucoes. acaba cfe instrucao, veja cada caso.
				// --------------------------------------------------------------------------------------------------
				// FETCH
				if (legal(pc)) { // pc valido
					ir = m[mem.traduzEndereco(pc, pm.status.tabelaPaginas)]; // <<<<<<<<<<<< busca posicao da memoria
																			// apontada por
																			// pc, guarda em ir

					if (trace) {
						System.out.println("                               pc: " + pc + "       exec: " + ir.opc + " "
								+ ir.r1 + " "
								+ ir.r2 + " " + ir.p);
					}
					// --------------------------------------------------------------------------------------------------
					// EXECUTA INSTRUCAO NO ir
					switch (ir.opc) { // conforme o opcode (código de operação) executa

						// Instrucoes de Busca e Armazenamento em Memoria
						case LDI: // Rd <- k
							reg[ir.r1] = ir.p;
							pc++;
							break;

						case LDD: // Rd <- [A]
							if (legal(ir.p)) {
								reg[ir.r1] = m[ir.p].p;
								pc++;
							}
							break;

						case LDX: // RD <- [RS] // NOVA
							if (legal(reg[ir.r2])) {
								reg[ir.r1] = m[reg[ir.r2]].p;
								pc++;
							}
							break;

						case STD: // [A] <- Rs
							if (legal(ir.p)) {
								m[ir.p].opc = Opcode.DATA;
								m[ir.p].p = reg[ir.r1];
								pc++;
							}
							;
							break;

						case STX: // [Rd] <-Rs
							if (legal(reg[ir.r1])) {
								m[reg[ir.r1]].opc = Opcode.DATA;
								m[reg[ir.r1]].p = reg[ir.r2];
								pc++;
							}
							;
							break;

						case MOVE: // RD <- RS
							reg[ir.r1] = reg[ir.r2];
							pc++;
							break;

						// Instrucoes Aritmeticas
						case ADD: // Rd <- Rd + Rs
							reg[ir.r1] = reg[ir.r1] + reg[ir.r2];
							testOverflow(reg[ir.r1]);
							pc++;
							break;

						case ADDI: // Rd <- Rd + k
							reg[ir.r1] = reg[ir.r1] + ir.p;
							testOverflow(reg[ir.r1]);
							pc++;
							break;

						case SUB: // Rd <- Rd - Rs
							reg[ir.r1] = reg[ir.r1] - reg[ir.r2];
							testOverflow(reg[ir.r1]);
							pc++;
							break;

						case SUBI: // RD <- RD - k // NOVA
							reg[ir.r1] = reg[ir.r1] - ir.p;
							testOverflow(reg[ir.r1]);
							pc++;
							break;

						case MULT: // Rd <- Rd * Rs
							reg[ir.r1] = reg[ir.r1] * reg[ir.r2];
							testOverflow(reg[ir.r1]);
							pc++;
							break;

						// Instrucoes JUMP
						case JMP: // PC <- k
							pc = ir.p;
							break;

						case JMPIG: // If Rc > 0 Then PC <- Rs Else PC <- PC +1
							if (reg[ir.r2] > 0) {
								pc = reg[ir.r1];
							} else {
								pc++;
							}
							break;

						case JMPIGK: // If RC > 0 then PC <- k else PC++
							if (reg[ir.r2] > 0) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;

						case JMPILK: // If RC < 0 then PC <- k else PC++
							if (reg[ir.r2] < 0) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;

						case JMPIEK: // If RC = 0 then PC <- k else PC++
							if (reg[ir.r2] == 0) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;

						case JMPIL: // if Rc < 0 then PC <- Rs Else PC <- PC +1
							if (reg[ir.r2] < 0) {
								pc = reg[ir.r1];
							} else {
								pc++;
							}
							break;

						case JMPIE: // If Rc = 0 Then PC <- Rs Else PC <- PC +1
							if (reg[ir.r2] == 0) {
								pc = reg[ir.r1];
							} else {
								pc++;
							}
							break;

						case JMPIM: // PC <- [A]
							pc = m[ir.p].p;
							break;

						case JMPIGM: // If RC > 0 then PC <- [A] else PC++
							if (reg[ir.r2] > 0) {
								pc = m[ir.p].p;
							} else {
								pc++;
							}
							break;

						case JMPILM: // If RC < 0 then PC <- k else PC++
							if (reg[ir.r2] < 0) {
								pc = m[ir.p].p;
							} else {
								pc++;
							}
							break;

						case JMPIEM: // If RC = 0 then PC <- k else PC++
							if (reg[ir.r2] == 0) {
								pc = m[ir.p].p;
							} else {
								pc++;
							}
							break;

						case JMPIGT: // If RS>RC then PC <- k else PC++
							if (reg[ir.r1] > reg[ir.r2]) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;

						// outras
						case STOP: // por enquanto, para execucao
							irpt = Interrupts.intSTOP;
							pm.desalocaProcesso(pm.status.id);
							break;

						case DATA:
							irpt = Interrupts.intInstrucaoInvalida;
							break;

						// Chamada de sistema
						case SYSCALL:
							sysCall.handle(); // <<<<< aqui desvia para rotina de chamada de sistema, no momento so
							// temos IO
							pc++;
							break;

						// Inexistente
						default:
							irpt = Interrupts.intInstrucaoInvalida;
							break;
					}
				}
				// VERIFICA INTERRUPÇÃO !!! - TERCEIRA FASE DO CICLO DE INSTRUÇÕES
				if (!(irpt == Interrupts.noInterrupt)) { // existe interrupção
					ih.handle(irpt, pc); // desvia para rotina de tratamento
					break; // break sai do loop da cpu
				}
			} // FIM DO CICLO DE UMA INSTRUÇÃO
		}
	}
	// ------------------ C P U - fim

	// ------------------- V M - constituida de CPU e MEMORIA
	// -------------------------- atributos e construcao da VM
	public class VM {
		public int tamMemoria;
		public int tamPagina;
		public Word[] m;
		public Memory mem;
		public CPU cpu;

		public VM(InterruptHandling ih, SysCallHandling sysCall) {
			// vm deve ser configurada com endereço de tratamento de interrupcoes e de
			// chamadas de sistema
			// cria memória
			tamMemoria = 1024;
			tamPagina = 4;
			mem = new Memory(tamMemoria, tamPagina);
			m = mem.memoriaFisica;
			// cria cpu
			cpu = new CPU(mem, ih, sysCall, true); // true liga debug
		}
	}
	// ------------------- V M - fim

	// --------------------H A R D W A R E - fim

	// ------------------- S O F T W A R E - inicio

	// ------------------- I N T E R R U P C O E S - rotinas de tratamento
	public class InterruptHandling {
		public void handle(Interrupts irpt, int pc) { // apenas avisa - todas interrupcoes neste momento finalizam o
			// programa
			switch (irpt) {
				case intEnderecoInvalido:
					System.out.println("                                               Endereco Invalido");
					break;
				case intInstrucaoInvalida:
					System.out.println("                                               Instrucao Invalida");
					break;
				case intOverflow:
					System.out.println("                                               Overflow");
					break;
				case intSTOP:
					System.out.println("                                               Parada solicitada");
					break;
				default:
					System.out.println("                                               Interrupcao nao tratada");
					break;
			}
		}
	}

	// ------------------- C H A M A D A S D E S I S T E M A - rotinas de tratamento
	// ----------------------
	public class SysCallHandling {
		private VM vm;

		public void setVM(VM _vm) {
			vm = _vm;
		}

		public void handle() { // handle system calls based on the operation code
			int syscallCode = vm.cpu.reg[8];
			int parameter = vm.cpu.reg[9];
			switch (syscallCode) {
				case 1: // Input
					Scanner scanner = new Scanner(System.in);
					System.out.print("Enter an integer: ");
					int input = scanner.nextInt();
					vm.cpu.mem.memoriaFisica[parameter].p = input; // Store input in the specified memory location
					break;
				case 2: // Output
					int output = vm.cpu.mem.memoriaFisica[parameter].p; // Read the value from the specified memory
																		// location
					System.out.println("Output: " + output);
					break;
				default:
					System.out.println("Unsupported system call: " + syscallCode);
					vm.cpu.irpt = Interrupts.intInstrucaoInvalida;
					break;
			}
		}
	}

	// ------------------ U T I L I T A R I O S D O S I S T E M A
	// -----------------------------------------
	// ------------------ load é invocado a partir de requisição do usuário

	public void loadProgram(Word[] p, Word[] m) {
		for (int i = 0; i < p.length; i++) {
			m[i].opc = p[i].opc;
			m[i].r1 = p[i].r1;
			m[i].r2 = p[i].r2;
			m[i].p = p[i].p;
		}
	}

	// -------------------------------------------------------------------------------------------------------
	// ------------------- S I S T E M A
	// --------------------------------------------------------------------

	public VM vm;
	public InterruptHandling ih;
	public SysCallHandling sysCall;
	public static Programas progs;

	public Sistema() { // a VM com tratamento de interrupções
		ih = new InterruptHandling();
		sysCall = new SysCallHandling();
		vm = new VM(ih, sysCall);
		sysCall.setVM(vm);
		progs = new Programas();
	}

	// ------------------- S I S T E M A - fim
	// --------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// ------------------- instancia e testa sistema
	public static void main(String args[]) {
		Sistema s = new Sistema();

		Scanner scanner = new Scanner(System.in);
		ProcessManager pm = s.vm.cpu.pm;

		System.out.println("\n==============================");
		System.out.println("          MENU PRINCIPAL       ");
		System.out.println("==============================\n");

		System.out.println("Escolha uma opção:");
		System.out.println(
				"new =  Criar um novo processo (fatorial, fibonacci10, fibonacciSYSCALL, fatorialSYSCALL, progMinimo, pb, pc, soma)");
		System.out.println("newall =  Criar todos os processos");
		System.out.println("rm =  Remover um processo");
		System.out.println("ps = Listar os processos");
		System.out.println("dump + id =  Mostrar o conteúdo de um processo");
		System.out.println("dumpM =  Mostrar o conteúdo da memória");
		System.out.println("dumpParcial =  Mostrar o conteúdo parcial da memória");
		System.out.println("exec + id =  Executar um processo");
		System.out.println("trace1 =  Ativar o trace");
		System.out.println("trace0 =  Desativar o trace");
		System.out.println("exit = Encerrar o programa");

		System.out.print("\nDigite a opção desejada: ");

		while (true) {
			String opcao = scanner.nextLine();

			switch (opcao.split(" ")[0]) {
				case "new":
					try {
						switch (opcao.split(" ")[1]) {
							case "fatorial":
								pm.criaProcesso(progs.fatorial);
								break;
							case "fibonacci10":
								pm.criaProcesso(progs.fibonacci10);
								break;
							case "fibonacciSYSCALL":
								pm.criaProcesso(progs.fibonacciSYSCALL);
								break;
							case "fatorialSYSCALL":
								pm.criaProcesso(progs.fatorialSYSCALL);
								break;
							case "progMinimo":
								pm.criaProcesso(progs.progMinimo);
								break;
							case "pb":
								pm.criaProcesso(progs.PB);
								break;
							case "pc":
								pm.criaProcesso(progs.PC);
								break;
							case "soma":
								pm.criaProcesso(progs.soma);
								break;
							default:
								System.out.println("Processo inválido");
								break;
						}
					} catch (ArrayIndexOutOfBoundsException e) {
						System.out.println("Argumentos insuficientes para criar um novo processo.");
					}
					break;

				//Esse método adiciona todos os processos
				case "newAll":
					try {
						pm.criaProcesso(progs.fatorial);
						pm.criaProcesso(progs.fibonacci10);
						pm.criaProcesso(progs.fibonacciSYSCALL);
						pm.criaProcesso(progs.fatorialSYSCALL);
						pm.criaProcesso(progs.progMinimo);
						pm.criaProcesso(progs.PB);
						pm.criaProcesso(progs.PC);
						pm.criaProcesso(progs.soma);
					} catch (Exception e) {
						System.out.println("Ocorreu um erro ao criar processos.");
					}
					break;

				case "rm":
					try {
						if (!pm.desalocaProcesso(Integer.parseInt(opcao.split(" ")[1]))) {
							System.out.println("Processo não encontrado");
						}
					} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
						System.out.println("Argumento inválido para remover processo.");
					}
					break;

				case "ps":
					boolean existeProcessos = false;
					for (int i = 0; i < pm.pcb.length; i++) {
						if (pm.pcb[i] != null) {
							existeProcessos = true;
							// imprime o nome do processo + o id
							System.out.println("Processo " + i);
						}
					}
					if (!existeProcessos) {
						System.out.println("Não existem processos.");
					}
					break;

				case "dump":
					try {
						int processId = Integer.parseInt(opcao.split(" ")[1]);
						if (pm.pcb[processId] != null) {
							System.out.println(
									"Processo " + processId + ": " + pm.pcb[processId].processState + " - "
											+ pm.pcb[processId].running);
							for (int i = 0; i < pm.pcb[processId].tabelaPaginas.length; i++) {
								s.vm.mem.dump(pm.pcb[processId].tabelaPaginas[i] * s.vm.tamPagina,
										(pm.pcb[processId].tabelaPaginas[i] + 1) * s.vm.tamPagina);
							}
						} else {
							System.out.println("Processo não encontrado");
						}
					} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
						System.out.println("Argumentos inválidos para realizar o dump do processo.");
					}
					break;
				case "dumpParcial":
					try {
						int inicio = Integer.parseInt(opcao.split(" ")[1]);
						int fim = Integer.parseInt(opcao.split(" ")[2]) + 1;
						if (fim > s.vm.tamMemoria) {
							fim = s.vm.tamMemoria;
						}
						s.vm.mem.dump(inicio, fim);
					} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
						System.out.println("Argumentos inválidos para realizar o dump parcial da memória.");
					}
					break;
				case "dumpM":
					s.vm.mem.dump(0, s.vm.tamMemoria);
					break;

				case "exec":
					try {
						if (!pm.executaProcesso(Integer.parseInt(opcao.split(" ")[1]))) {
							System.out.println("Processo não encontrado");
						}
					} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
						System.out.println("Argumento inválido para executar o processo.");
					}
					break;

				case "trace1":
					s.vm.cpu.trace = true;
					break;
				case "trace0":
					s.vm.cpu.trace = false;
					break;
				case "exit":
					System.exit(0);
					break;
				default:
					System.out.println("Opção inválida");
					break;
			}
		}
	}

	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// --------------- P R O G R A M A S - não fazem parte do sistema
	// esta classe representa programas armazenados (como se estivessem em disco)
	// que podem ser carregados para a memória (load faz isto)

	public class Word { // cada posicao da memoria tem uma instrucao (ou um dado)
		public Opcode opc; //
		public int r1; // indice do primeiro registrador da operacao (Rs ou Rd cfe opcode na tabela)
		public int r2; // indice do segundo registrador da operacao (Rc ou Rs cfe operacao)
		public int p; // parametro para instrucao (k ou A cfe operacao), ou o dado, se opcode = DADO

		public Word(Opcode _opc, int _r1, int _r2, int _p) { // vide definição da VM - colunas vermelhas da tabela
			opc = _opc;
			r1 = _r1;
			r2 = _r2;
			p = _p;
		}
	}

	public class Programas {
		public Word[] fatorial = new Word[] {
				// este fatorial so aceita valores positivos. nao pode ser zero
				new Word(Opcode.LDI, 0, -1, 4), // 0 r0 é valor a calcular fatorial
				new Word(Opcode.LDI, 1, -1, 1), // 1 r1 é 1 para multiplicar (por r0)
				new Word(Opcode.LDI, 6, -1, 1), // 2 r6 é 1 para ser o decremento
				new Word(Opcode.LDI, 7, -1, 8), // 3 r7 tem posicao de stop do programa = 8
				new Word(Opcode.JMPIE, 7, 0, 0), // 4 se r0=0 pula para r7(=8)
				new Word(Opcode.MULT, 1, 0, -1), // 5 r1 = r1 * r0
				new Word(Opcode.SUB, 0, 6, -1), // 6 decrementa r0 1
				new Word(Opcode.JMP, -1, -1, 4), // 7 vai p posicao 4
				new Word(Opcode.STD, 1, -1, 10), // 8 coloca valor de r1 na posição 10
				new Word(Opcode.STOP, -1, -1, -1), // 9 stop
				new Word(Opcode.DATA, -1, -1, -1) }; // 10 ao final o valor do fatorial estará na posição 10 da memória

		public Word[] progMinimo = new Word[] {
				new Word(Opcode.LDI, 0, -1, 999),
				new Word(Opcode.STD, 0, -1, 10),
				new Word(Opcode.STD, 0, -1, 11),
				new Word(Opcode.STD, 0, -1, 12),
				new Word(Opcode.STD, 0, -1, 13),
				new Word(Opcode.STD, 0, -1, 14),
				new Word(Opcode.STOP, -1, -1, -1) };

		public Word[] fibonacci10 = new Word[] { // mesmo que prog exemplo, so que usa r0 no lugar de r8
				new Word(Opcode.LDI, 1, -1, 0),
				new Word(Opcode.STD, 1, -1, 20),
				new Word(Opcode.LDI, 2, -1, 1),
				new Word(Opcode.STD, 2, -1, 21),
				new Word(Opcode.LDI, 0, -1, 22),
				new Word(Opcode.LDI, 6, -1, 6),
				new Word(Opcode.LDI, 7, -1, 31),
				new Word(Opcode.LDI, 3, -1, 0),
				new Word(Opcode.ADD, 3, 1, -1),
				new Word(Opcode.LDI, 1, -1, 0),
				new Word(Opcode.ADD, 1, 2, -1),
				new Word(Opcode.ADD, 2, 3, -1),
				new Word(Opcode.STX, 0, 2, -1),
				new Word(Opcode.ADDI, 0, -1, 1),
				new Word(Opcode.SUB, 7, 0, -1),
				new Word(Opcode.JMPIG, 6, 7, -1),
				new Word(Opcode.STOP, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1), // POS 20
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1) }; // ate aqui - serie de fibonacci ficara armazenada

		public Word[] fatorialSYSCALL = new Word[] {
				new Word(Opcode.LDI, 0, -1, 7), // numero para colocar na memoria
				new Word(Opcode.STD, 0, -1, 50),
				new Word(Opcode.LDD, 0, -1, 50),
				new Word(Opcode.LDI, 1, -1, -1),
				new Word(Opcode.LDI, 2, -1, 13), // SALVAR POS STOP
				new Word(Opcode.JMPIL, 2, 0, -1), // caso negativo pula pro STD
				new Word(Opcode.LDI, 1, -1, 1),
				new Word(Opcode.LDI, 6, -1, 1),
				new Word(Opcode.LDI, 7, -1, 13),
				new Word(Opcode.JMPIE, 7, 0, 0), // POS 9 pula pra STD (Stop-1)
				new Word(Opcode.MULT, 1, 0, -1),
				new Word(Opcode.SUB, 0, 6, -1),
				new Word(Opcode.JMP, -1, -1, 9), // pula para o JMPIE
				new Word(Opcode.STD, 1, -1, 18),
				new Word(Opcode.LDI, 8, -1, 2), // escrita
				new Word(Opcode.LDI, 9, -1, 18), // endereco com valor a escrever
				new Word(Opcode.SYSCALL, -1, -1, -1),
				new Word(Opcode.STOP, -1, -1, -1), // POS 17
				new Word(Opcode.DATA, -1, -1, -1) };// POS 18

		public Word[] fibonacciSYSCALL = new Word[] { // mesmo que prog exemplo, so que usa r0 no lugar de r8
				new Word(Opcode.LDI, 8, -1, 1), // leitura
				new Word(Opcode.LDI, 9, -1, 100), // endereco a guardar
				new Word(Opcode.SYSCALL, -1, -1, -1),
				new Word(Opcode.LDD, 7, -1, 100), // numero do tamanho do fib
				new Word(Opcode.LDI, 3, -1, 0),
				new Word(Opcode.ADD, 3, 7, -1),
				new Word(Opcode.LDI, 4, -1, 36), // posicao para qual ira pular (stop) *
				new Word(Opcode.LDI, 1, -1, -1), // caso negativo
				new Word(Opcode.STD, 1, -1, 41),
				new Word(Opcode.JMPIL, 4, 7, -1), // pula pra stop caso negativo *
				new Word(Opcode.JMPIE, 4, 7, -1), // pula pra stop caso 0
				new Word(Opcode.ADDI, 7, -1, 41), // fibonacci + posição do stop
				new Word(Opcode.LDI, 1, -1, 0),
				new Word(Opcode.STD, 1, -1, 41), // 25 posicao de memoria onde inicia a serie de fibonacci gerada
				new Word(Opcode.SUBI, 3, -1, 1), // se 1 pula pro stop
				new Word(Opcode.JMPIE, 4, 3, -1),
				new Word(Opcode.ADDI, 3, -1, 1),
				new Word(Opcode.LDI, 2, -1, 1),
				new Word(Opcode.STD, 2, -1, 42),
				new Word(Opcode.SUBI, 3, -1, 2), // se 2 pula pro stop
				new Word(Opcode.JMPIE, 4, 3, -1),
				new Word(Opcode.LDI, 0, -1, 43),
				new Word(Opcode.LDI, 6, -1, 25), // salva posição de retorno do loop
				new Word(Opcode.LDI, 5, -1, 0), // salva tamanho
				new Word(Opcode.ADD, 5, 7, -1),
				new Word(Opcode.LDI, 7, -1, 0), // zera (inicio do loop)
				new Word(Opcode.ADD, 7, 5, -1), // recarrega tamanho
				new Word(Opcode.LDI, 3, -1, 0),
				new Word(Opcode.ADD, 3, 1, -1),
				new Word(Opcode.LDI, 1, -1, 0),
				new Word(Opcode.ADD, 1, 2, -1),
				new Word(Opcode.ADD, 2, 3, -1),
				new Word(Opcode.STX, 0, 2, -1),
				new Word(Opcode.ADDI, 0, -1, 1),
				new Word(Opcode.SUB, 7, 0, -1),
				new Word(Opcode.JMPIG, 6, 7, -1), // volta para o inicio do loop
				new Word(Opcode.STOP, -1, -1, -1), // POS 36
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1), // POS 41
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1)
		};

		public Word[] PB = new Word[] {
				// dado um inteiro em alguma posição de memória,
				// se for negativo armazena -1 na saída; se for positivo responde o fatorial do
				// número na saída
				new Word(Opcode.LDI, 0, -1, 7), // numero para colocar na memoria
				new Word(Opcode.STD, 0, -1, 50),
				new Word(Opcode.LDD, 0, -1, 50),
				new Word(Opcode.LDI, 1, -1, -1),
				new Word(Opcode.LDI, 2, -1, 13), // SALVAR POS STOP
				new Word(Opcode.JMPIL, 2, 0, -1), // caso negativo pula pro STD
				new Word(Opcode.LDI, 1, -1, 1),
				new Word(Opcode.LDI, 6, -1, 1),
				new Word(Opcode.LDI, 7, -1, 13),
				new Word(Opcode.JMPIE, 7, 0, 0), // POS 9 pula pra STD (Stop-1)
				new Word(Opcode.MULT, 1, 0, -1),
				new Word(Opcode.SUB, 0, 6, -1),
				new Word(Opcode.JMP, -1, -1, 9), // pula para o JMPIE
				new Word(Opcode.STD, 1, -1, 15),
				new Word(Opcode.STOP, -1, -1, -1), // POS 14
				new Word(Opcode.DATA, -1, -1, -1) }; // POS 15

		public Word[] PC = new Word[] {
				// Para um N definido (10 por exemplo)
				// o programa ordena um vetor de N números em alguma posição de memória;
				// ordena usando bubble sort
				// loop ate que não swap nada
				// passando pelos N valores
				// faz swap de vizinhos se da esquerda maior que da direita
				new Word(Opcode.LDI, 7, -1, 5), // TAMANHO DO BUBBLE SORT (N)
				new Word(Opcode.LDI, 6, -1, 5), // aux N
				new Word(Opcode.LDI, 5, -1, 46), // LOCAL DA MEMORIA
				new Word(Opcode.LDI, 4, -1, 47), // aux local memoria
				new Word(Opcode.LDI, 0, -1, 4), // colocando valores na memoria
				new Word(Opcode.STD, 0, -1, 46),
				new Word(Opcode.LDI, 0, -1, 3),
				new Word(Opcode.STD, 0, -1, 47),
				new Word(Opcode.LDI, 0, -1, 5),
				new Word(Opcode.STD, 0, -1, 48),
				new Word(Opcode.LDI, 0, -1, 1),
				new Word(Opcode.STD, 0, -1, 49),
				new Word(Opcode.LDI, 0, -1, 2),
				new Word(Opcode.STD, 0, -1, 50), // colocando valores na memoria até aqui - POS 13
				new Word(Opcode.LDI, 3, -1, 25), // Posicao para pulo CHAVE 1
				new Word(Opcode.STD, 3, -1, 99),
				new Word(Opcode.LDI, 3, -1, 22), // Posicao para pulo CHAVE 2
				new Word(Opcode.STD, 3, -1, 98),
				new Word(Opcode.LDI, 3, -1, 38), // Posicao para pulo CHAVE 3
				new Word(Opcode.STD, 3, -1, 97),
				new Word(Opcode.LDI, 3, -1, 25), // Posicao para pulo CHAVE 4 (não usada)
				new Word(Opcode.STD, 3, -1, 96),
				new Word(Opcode.LDI, 6, -1, 0), // r6 = r7 - 1 POS 22
				new Word(Opcode.ADD, 6, 7, -1),
				new Word(Opcode.SUBI, 6, -1, 1), // ate aqui
				new Word(Opcode.JMPIEM, -1, 6, 97), // CHAVE 3 para pular quando r7 for 1 e r6 0 para interomper o loop
				// de vez
				// do programa
				new Word(Opcode.LDX, 0, 5, -1), // r0 e r1 pegando valores das posições da memoria POS 26
				new Word(Opcode.LDX, 1, 4, -1),
				new Word(Opcode.LDI, 2, -1, 0),
				new Word(Opcode.ADD, 2, 0, -1),
				new Word(Opcode.SUB, 2, 1, -1),
				new Word(Opcode.ADDI, 4, -1, 1),
				new Word(Opcode.SUBI, 6, -1, 1),
				new Word(Opcode.JMPILM, -1, 2, 99), // LOOP chave 1 caso neg procura prox
				new Word(Opcode.STX, 5, 1, -1),
				new Word(Opcode.SUBI, 4, -1, 1),
				new Word(Opcode.STX, 4, 0, -1),
				new Word(Opcode.ADDI, 4, -1, 1),
				new Word(Opcode.JMPIGM, -1, 6, 99), // LOOP chave 1 POS 38
				new Word(Opcode.ADDI, 5, -1, 1),
				new Word(Opcode.SUBI, 7, -1, 1),
				new Word(Opcode.LDI, 4, -1, 0), // r4 = r5 + 1 POS 41
				new Word(Opcode.ADD, 4, 5, -1),
				new Word(Opcode.ADDI, 4, -1, 1), // ate aqui
				new Word(Opcode.JMPIGM, -1, 7, 98), // LOOP chave 2
				new Word(Opcode.STOP, -1, -1, -1), // POS 45
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1),
				new Word(Opcode.DATA, -1, -1, -1) };

		public Word[] soma = new Word[] {
				new Word(Opcode.LDI, 0, -1, 5), // Carrega o valor 5 para o registrador 0 (r0)
				new Word(Opcode.ADDI, 0, -1, 5), // Soma 5 ao valor atual em r0
				new Word(Opcode.STD, 0, -1, 5),
				new Word(Opcode.STOP, -1, -1, -1)
		};
	}
}