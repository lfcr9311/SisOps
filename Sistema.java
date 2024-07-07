// PUCRS - Escola Politécnica - Sistemas Operacionais
// Prof. Fernando Dotti
// Código fornecido como parte da solução do projeto de Sistemas Operacionais
//
// VM
//    HW = memória, cpu
//    SW = tratamento int e chamada de sistema
// Funcionalidades de carga, execução e dump de memória

import java.util.*;
import java.util.concurrent.Semaphore;

public class Sistema {

	// -------------------------------------------------------------------------------------------------------
	// --------------------- H A R D W A R E - definicoes de HW
	// ----------------------------------------------

	// -------------------------------------------------------------------------------------------------------
	// --------------------- M E M O R I A - definicoes de palavra de memoria,
	// memória ----------------------

	public static class Memory {
		public final int tamMemoria;
		public final int tamPagina;
		public final int numeroFrames;
		public final boolean[] listaFrames;
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

		public int enderecoFisico(int enderecoLogico, int[] tabelaPaginas) {
			int numeroPagina = enderecoLogico / tamPagina;
			int offset = enderecoLogico % tamPagina;
			if (numeroPagina >= tabelaPaginas.length) {
				System.out.println("Endereço de memória inválido.");
				return -1;
			}
			int frame = tabelaPaginas[numeroPagina];
			return frame * tamPagina + offset;
		}

		public int[] aloca(int numPalavras) {

			int numPaginas = numPalavras / tamPagina;
			if (numPalavras % tamPagina != 0) {
				numPaginas++;
			}
			int[] tabelaPaginas = new int[numPaginas];
			int numFrames = 0;
			for (int i = 0; i < listaFrames.length; i++) {
				if (listaFrames[i]) {
					tabelaPaginas[numFrames] = i;
					listaFrames[i] = false;
					numFrames++;
				}
				if (numFrames == numPaginas) {
					break;
				}
			}
			if (numFrames < numPaginas) {
				System.out.println("Memória insuficiente para alocar o processo.");

				for (int i = 0; i < numFrames; i++) {
					listaFrames[tabelaPaginas[i]] = true;
				}
				return null;
			}
			return tabelaPaginas;
		}

		public void desaloca(int[] tabelaPaginas) {
			for (int i = 0; i < tabelaPaginas.length; i++) {
				listaFrames[i] = true;
			}
		}

		public void limpaMemoria() {
			for (int i = 0; i < tamMemoria; i++) {
				memoriaFisica[i].opc = Opcode.___;
				memoriaFisica[i].r1 = -1;
				memoriaFisica[i].r2 = -1;
				memoriaFisica[i].p = -1;
			}
			for (int i = 0; i < listaFrames.length; i++) {
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

	public class ProcessManager extends Thread {
		private final Memory memoria;
		private int id;
		private final ProcessControlBlock[] pcb;
		private ProcessControlBlock status;
		private Queue<String> intel;
		private Semaphore semaforoIntel;
		private List<ProcessControlBlock> prontos;
		private Semaphore semaforoCpu;

		public ProcessManager(Memory mem, Queue<String> intel, Semaphore semaforoIntel, List<ProcessControlBlock> prontos,
				Semaphore semaforoCpu) {
			id = 0;
			this.memoria = mem;
			this.intel = intel;
			this.semaforoIntel = semaforoIntel;
			this.prontos = prontos;
			this.semaforoCpu = semaforoCpu;
			pcb = new ProcessControlBlock[mem.numeroFrames];
		}

		public boolean criaProcesso(Word[] program) {
			int[] tabelaPaginas = memoria.aloca(program.length);
			if (tabelaPaginas == null) {
				System.out.println("Memória insuficiente para alocar o processo.");
			} else {
				ProcessControlBlock newpcb = new ProcessControlBlock(id);
				newpcb.tabelaPaginas = tabelaPaginas;
				newpcb.processState = true;
				newpcb.running = false;
				this.pcb[id] = newpcb;
				id++;

				for (int i = 0; i < program.length; i++) {
					int enderecoFisico = memoria.enderecoFisico(i, tabelaPaginas);
					memoria.memoriaFisica[enderecoFisico].opc = program[i].opc;
					memoria.memoriaFisica[enderecoFisico].r1 = program[i].r1;
					memoria.memoriaFisica[enderecoFisico].r2 = program[i].r2;
					memoria.memoriaFisica[enderecoFisico].p = program[i].p;
				}
				return true;
			}
			return false;
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
			if (pcb[id] == null) {
				return false;
			} else {
				try {
					semaforoCpu.acquire();
					prontos.add(pcb[id]);
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					semaforoCpu.release();
				}
				return true;
			}
		}

		public ProcessControlBlock salvaProcessoCpu() {
			if (status != null) {
				status.pc = vm.cpu.pc;
				status.registrador = vm.cpu.reg;
				return status;
			}
			return null;
		}

		public ProcessControlBlock setStatus(ProcessControlBlock pcb) {
			try {
				semaforoCpu.acquire();
				status = pcb;
				return status;
			} catch (InterruptedException e) {
				e.printStackTrace();
				return null;
			} finally {
				semaforoCpu.release();
			}
		}

		public void run() {

			while (true) {
				try {
					semaforoIntel.acquire();
					String opcao = intel.poll();
					if (opcao != null)
						switch (opcao.split(" ")[0]) {
							case "new":
								try {
									switch (opcao.split(" ")[1]) {
										case "fatorial":
											criaProcesso(progs.fatorial);
											break;
										case "fibonacci10":
											criaProcesso(progs.fibonacci10);
											break;
										case "fibonacciSYSCALL":
											criaProcesso(progs.fibonacciSYSCALL);
											break;
										case "fatorialSYSCALL":
											criaProcesso(progs.fatorialSYSCALL);
											break;
										case "progMinimo":
											criaProcesso(progs.progMinimo);
											break;
										case "pb":
											criaProcesso(progs.PB);
											break;
										case "soma":
											criaProcesso(progs.soma);
											break;
										case "subtrai":
											criaProcesso(progs.subtrai);
											break;
										default:
											System.out.println("Processo inválido");
											break;
									}
								} catch (ArrayIndexOutOfBoundsException e) {
									System.out.println("Argumentos insuficientes para criar um novo processo.");
								}
								break;

							// Esse método adiciona todos os processos
							case "newAll":
								try {
									criaProcesso(progs.fatorial);
									criaProcesso(progs.fibonacci10);
									criaProcesso(progs.fibonacciSYSCALL);
									criaProcesso(progs.fatorialSYSCALL);
									criaProcesso(progs.progMinimo);
									criaProcesso(progs.PB);
									criaProcesso(progs.soma);
								} catch (Exception e) {
									System.out.println("Ocorreu um erro ao criar processos.");
								}
								break;

							// Metodo adicional caso queira limpar a memoria
							// desacola todos processos e limpa a memoria
							case "limpaMemoria":
								status = null;
								for (int i = 0; i < pcb.length; i++) {
									if (pcb[i] != null) {
										desalocaProcesso(i);
									}
								}
								memoria.limpaMemoria();
								break;

							case "rm":
								try {
									if (!desalocaProcesso(Integer.parseInt(opcao.split(" ")[1]))) {
										System.out.println("Processo não encontrado");
									}
								} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
									System.out.println("Não foi possível remover o processo. Argumentos inválidos.");
								}
								break;

							case "ps":
								boolean existeProcessos = false;
								for (int i = 0; i < pcb.length; i++) {
									if (pcb[i] != null) {
										existeProcessos = true;
										System.out.println("Processo " + i);
									}
								}
								if (existeProcessos == false) {
									System.out.println("Não existem processos.");
								}
								break;

							case "dump":
								try {
									int processId = Integer.parseInt(opcao.split(" ")[1]);
									if (pcb[processId] != null) {
										System.out.println(
												"Processo " + processId + ": " + pcb[processId].processState + " - "
														+ pcb[processId].running);
										for (int i = 0; i < pcb[processId].tabelaPaginas.length; i++) {
											memoria.dump(pcb[processId].tabelaPaginas[i] * vm.tamPagina,
													(pcb[processId].tabelaPaginas[i] + 1) * vm.tamPagina);
										}
									} else {
										System.out.println("Processo não encontrado");
									}
								} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
									System.out.println("Argumentos inválidos para realizar o dump do processo.");
								}
								break;

							// Metodo adicional caso queira imprimir uma parte da memoria
							case "dumpParcial":
								try {
									int inicio = Integer.parseInt(opcao.split(" ")[1]);
									int fim = Integer.parseInt(opcao.split(" ")[2]) + 1;
									if (fim > vm.tamMemoria) {
										fim = vm.tamMemoria;
									}
									vm.mem.dump(inicio, fim);
								} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
									System.out.println("Não foi possível realizar o dump parcial da memória.");
								}
								break;
							case "dumpM":
								vm.mem.dump(0, vm.tamMemoria);
								break;

							case "exec":
								try {
									if (!executaProcesso(Integer.parseInt(opcao.split(" ")[1]))) {
										System.out.println("Processo não encontrado");
									}
								} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
									System.out.println("Argumento inválido para executar o processo.");
								}
								break;
							case "execAll":
								try {
									for (int i = 0; i < pcb.length; i++) {
										if (pcb[i] != null) {
											executaProcesso(i);
										}
									}
								} catch (Exception e) {
									System.out.println("Ocorreu um erro ao executar os processos.");
								}
								break;

							case "mostraFrame":
								for (int i = 0; i < vm.mem.numeroFrames; i++) {
									System.out.println("Frame " + i + ": " + vm.mem.listaFrames[i]);
								}
								break;

							case "trace1":
								vm.cpu.trace = true;
								break;
							case "trace0":
								vm.cpu.trace = false;
								break;
							case "exit":
								System.exit(0);
								break;
							default:
								System.out.println("Opção inválida");
								break;
						}
				} catch (Exception e) {
					System.out.println("Ocorreu um erro ao pegar a opção da fila." + e.getMessage());
				} finally {
					semaforoIntel.release();
				}
			}
		}
	}

	public static class ProcessControlBlock {
		public int[] tabelaPaginas;
		public boolean processState;
		public boolean running;
		public int pc;
		public int id;
		public int[] registrador;

		public ProcessControlBlock(int id) {
			tabelaPaginas = null;
			processState = false;
			running = false;
			pc = 0;
			this.id = id;
			registrador = new int[10];
		}
	}

	// -------------------------------------------------------------------------------------------------------
	// --------------------- C P U - definicoes da CPU
	// -----------------------------------------------------

	public class CPU extends Thread {
		private int fatiaTempo;
		private int maxInt; // valores maximo e minimo para inteiros nesta cpu
		private int minInt;
		private boolean trace;
		private int pc; // ... composto de program counter,
		private Word ir; // instruction register,
		private int[] reg; // registradores da CPU
		private Interrupts irpt; // durante instrucao, interrupcao pode ser sinalizada
		private int base; // base e limite de acesso na memoria
		private int limite; // por enquanto toda memoria pode ser acessada pelo processo rodando

		private Memory mem; // mem tem funcoes de dump e o array m de memória 'fisica'
		private Word[] m; // CPU acessa MEMORIA, guarda referencia a 'm'. m nao muda. semre será um array
		// de palavras

		private InterruptHandling ih; // significa desvio para rotinas de tratamento de Int - se int ligada, desvia
		private SysCallHandling sysCall; // significa desvio para tratamento de chamadas de sistema - SYSCALL
		private boolean debug; // se true entao mostra cada instrucao em execucao
		public ProcessManager pm;
		public Semaphore semaforoIntel;
		public Semaphore semaforoCpu;
		public Queue<String> intel;
		public List<ProcessControlBlock> pcb;

		public CPU(Memory _mem, InterruptHandling _ih, SysCallHandling _sysCall, boolean _debug, Semaphore semaforoIntel,
				Queue<String> _intel) {
			fatiaTempo = 2;
			maxInt = 32767; // capacidade de representacao modelada
			minInt = -32767; // se exceder deve gerar interrupcao de overflow
			mem = _mem; // usa mem para acessar funcoes auxiliares (dump)
			m = mem.memoriaFisica; // usa o atributo 'm' para acessar a memoria.
			reg = new int[10]; // aloca o espaço dos registradores - regs 8 e 9 usados somente para IO
			ih = _ih; // aponta para rotinas de tratamento de int
			sysCall = _sysCall; // aponta para rotinas de tratamento de chamadas de sistema
			debug = _debug; // se true, print da instrucao em execucao
			intel = _intel;
			this.semaforoIntel = semaforoIntel;
			pcb = new ArrayList<ProcessControlBlock>();
			semaforoCpu = new Semaphore(1);
			pm = new ProcessManager(mem, _intel, semaforoIntel, pcb, semaforoCpu);
			trace = true;
		}

		private boolean legal(int e) {
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

		public void setContext(int _base, int _limite, int _pc, int _reg[]) { // no futuro esta funcao vai ter que ser
			base = _base; // expandida para setar todo contexto de execucao,
			limite = _limite; // agora, setamos somente os registradores base,
			reg = _reg; // limite e pc (deve ser zero nesta versao)
			pc = _pc; // limite e pc (deve ser zero nesta versao)
			irpt = Interrupts.noInterrupt; // reset da interrupcao registrada
		}

		public void run() {
			pm.start();
			while (true) {
				ProcessControlBlock processo = null;

				try {
					semaforoCpu.acquire();
					if (!pcb.isEmpty()) {
						processo = pcb.removeFirst();
					}
				} catch (Exception e) {
					e.printStackTrace();
				} finally {
					semaforoCpu.release();
				}

				if (processo != null) {
					pm.setStatus(processo);
					setContext(0, 0, processo.pc, processo.registrador);
					executaCpu();
				}
			}
		}

		public void executaCpu() {
			while (true) {
				if (legal(pc)) {
					ir = m[mem.enderecoFisico(pc, pm.status.tabelaPaginas)];
					if (trace) {
						System.out.println("                               pc: " + pc + "       exec: " + ir.opc + " "
								+ ir.r1 + " "
								+ ir.r2 + " " + ir.p);
					}
					switch (ir.opc) {
						case LDI:
							reg[ir.r1] = ir.p;
							pc++;
							break;
						case LDD:
							if (legal(mem.enderecoFisico(ir.p, pm.status.tabelaPaginas))) {
								reg[ir.r1] = m[mem.enderecoFisico(ir.p, pm.status.tabelaPaginas)].p;
								pc++;
							}
							break;
						case LDX:
							if (legal(mem.enderecoFisico(reg[ir.r2], pm.status.tabelaPaginas))) {
								reg[ir.r1] = m[mem.enderecoFisico(reg[ir.r2], pm.status.tabelaPaginas)].p;
								pc++;
							}
							break;
						case STD:
							if (legal(mem.enderecoFisico(ir.p, pm.status.tabelaPaginas))) {
								m[mem.enderecoFisico(ir.p, pm.status.tabelaPaginas)].opc = Opcode.DATA;
								m[mem.enderecoFisico(ir.p, pm.status.tabelaPaginas)].p = reg[ir.r1];
								pc++;
							}
							break;
						case STX:
							if (legal(mem.enderecoFisico(reg[ir.r1], pm.status.tabelaPaginas))) {
								m[mem.enderecoFisico(reg[ir.r1], pm.status.tabelaPaginas)].opc = Opcode.DATA;
								m[mem.enderecoFisico(reg[ir.r1], pm.status.tabelaPaginas)].p = reg[ir.r2];
								pc++;
							}
							break;
						case MOVE:
							reg[ir.r1] = reg[ir.r2];
							pc++;
							break;
						case ADD:
							reg[ir.r1] = reg[ir.r1] + reg[ir.r2];
							testOverflow(reg[ir.r1]);
							pc++;
							break;
						case ADDI:
							reg[ir.r1] = reg[ir.r1] + ir.p;
							testOverflow(reg[ir.r1]);
							pc++;
							break;
						case SUB:
							reg[ir.r1] = reg[ir.r1] - reg[ir.r2];
							testOverflow(reg[ir.r1]);
							pc++;
							break;
						case SUBI:
							reg[ir.r1] = reg[ir.r1] - ir.p;
							testOverflow(reg[ir.r1]);
							pc++;
							break;
						case MULT:
							reg[ir.r1] = reg[ir.r1] * reg[ir.r2];
							testOverflow(reg[ir.r1]);
							pc++;
							break;
						case JMP:
							pc = ir.p;
							break;
						case JMPIG:
							if (reg[ir.r2] > 0) {
								pc = reg[ir.r1];
							} else {
								pc++;
							}
							break;
						case JMPIGK:
							if (reg[ir.r2] > 0) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;
						case JMPILK:
							if (reg[ir.r2] < 0) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;
						case JMPIEK:
							if (reg[ir.r2] == 0) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;
						case JMPIL:
							if (reg[ir.r2] < 0) {
								pc = reg[ir.r1];
							} else {
								pc++;
							}
							break;
						case JMPIE:
							if (reg[ir.r2] == 0) {
								pc = reg[ir.r1];
							} else {
								pc++;
							}
							break;
						case JMPIM:
							pc = m[mem.enderecoFisico(ir.p, pm.status.tabelaPaginas)].p;
							break;
						case JMPIGM:
							if (reg[ir.r2] > 0) {
								pc = m[mem.enderecoFisico(ir.p, pm.status.tabelaPaginas)].p;
							} else {
								pc++;
							}
							break;
						case JMPILM:
							if (reg[ir.r2] < 0) {
								pc = m[mem.enderecoFisico(ir.p, pm.status.tabelaPaginas)].p;
							} else {
								pc++;
							}
							break;
						case JMPIEM:
							if (reg[ir.r2] == 0) {
								pc = m[mem.enderecoFisico(ir.p, pm.status.tabelaPaginas)].p;
							} else {
								pc++;
							}
							break;
						case JMPIGT:
							if (reg[ir.r1] > reg[ir.r2]) {
								pc = ir.p;
							} else {
								pc++;
							}
							break;
						case STOP:
							irpt = Interrupts.intSTOP;
							pm.desalocaProcesso(pm.status.id);
							break;
						case DATA:
							irpt = Interrupts.intInstrucaoInvalida;
							break;
						case SYSCALL:
							pc++;
							break;
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
		public ShellThread shell;

		public VM(InterruptHandling ih, SysCallHandling sysCall) {
			LinkedList<String> intel = new LinkedList<String>();
			Semaphore semaforoIntel = new Semaphore(1);
			shell = new ShellThread(intel, semaforoIntel);
			tamMemoria = 1024;
			tamPagina = 8;
			mem = new Memory(tamMemoria, tamPagina);
			m = mem.memoriaFisica;
			// cria cpu
			cpu = new CPU(mem, ih, sysCall, true, semaforoIntel, intel); // true liga debug
		}
	}
	// ------------------- V M - fim

	// --------------------H A R D W A R E - fim

	// ------------------- S O F T W A R E - inicio

	// ------------------- I N T E R R U P C O E S - rotinas de tratamento
	public static class InterruptHandling {
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
					vm.cpu.mem.memoriaFisica[parameter].p = input;
					break;
				case 2: // Output
					int output = vm.cpu.mem.memoriaFisica[parameter].p;

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

	public class ShellThread extends Thread {

		private Queue<String> intel;
		private Semaphore semaforoIntel;

		ShellThread(LinkedList<String> intel, Semaphore semaforoIntel) {
			this.semaforoIntel = semaforoIntel;
			this.intel = intel;
		}

		public void run() {
			Scanner scanner = new Scanner(System.in);

			System.out.println("\n==============================");
			System.out.println("          MENU PRINCIPAL       ");
			System.out.println("==============================\n");
			System.out.println("Tamanho da memória: " + vm.mem.tamMemoria);
			System.out.println("Tamanho da página: " + vm.mem.tamPagina);
			System.out.println("Número de frames: " + vm.mem.numeroFrames);
			System.out.println("\n");

			System.out.println("Escolha uma opção:");

			System.out.println("new =  Criar um novo processo " +
					"(fatorial, fibonacci10, " +
					"fibonacciSYSCALL, fatorialSYSCALL, " +
					"progMinimo, pb, pc, soma, subtrai)");

			System.out.println("ps = Listar os processos");
			System.out.println("rm =  Remover um processo");
			System.out.println("dump + id =  Mostrar o conteúdo de um processo");
			System.out.println("dumpM =  Mostrar o conteúdo da memória");
			System.out.println("exec + id =  Executar um processo");
			System.out.println("trace1 =  Ativar o trace");
			System.out.println("trace0 =  Desativar o trace");
			System.out.println("newAll =  Criar todos os processos");
			System.out.println("dumpParcial =  Mostrar o conteúdo parcial da memória");
			System.out.println("limpaMemoria =  Limpar toda a memória + processos");
			System.out.println("mostraFrame =  Mostrar os frames da memória");
			System.out.println("exit = Encerrar o programa");

			System.out.print("\nDigite a opção desejada: ");

			while (true) {
				String opcao = scanner.nextLine();
				try {
					semaforoIntel.acquire();
					intel.add(opcao);
					semaforoIntel.release();
				} catch (Exception e) {
					System.out.println("Ocorreu um erro ao adicionar a opção na fila.");
				} finally {
					semaforoIntel.release();
				}
			}
		}
	}

	// ------------------- S I S T E M A - fim
	// --------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// ------------------- instancia e testa sistema
	public static void main(String[] args) {
		Sistema s = new Sistema();
		s.vm.shell.start();
		s.vm.cpu.start();
	}

	public static class Word { // cada posicao da memoria tem uma instrucao (ou um dado)
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

	public static class Programas {
		public Word[] fatorial = new Word[] {
				new Word(Opcode.LDI, 0, -1, 4), // 0 r0 é valor a calcular fatorial
				new Word(Opcode.LDI, 1, -1, 1), // 1 r1 é 1 para multiplicar (por r0)
				new Word(Opcode.LDI, 6, -1, 1), // 2 r6 é 1 para ser o decremento
				new Word(Opcode.LDI, 7, -1, 8), // 3 r7 tem posicao de stop do programa = 8
				new Word(Opcode.JMPIE, 7, 0, 0), // 4 se r0=0 pula para r7(=8)
				new Word(Opcode.MULT, 1, 0, -1), // 5 r1 = r1 * r0
				new Word(Opcode.SUB, 0, 6, -1), // 6 decrementa r0 1
				new Word(Opcode.JMP, -1, -1, 4), // 7 vai p posicao 4
				new Word(Opcode.STD, 1, -1, 25), // 8 coloca valor de r1 na posição 10
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
				new Word(Opcode.LDI, 7, -1, 25),
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

		public Word[] soma = new Word[] {
				new Word(Opcode.LDI, 0, -1, 5),
				new Word(Opcode.ADDI, 0, -1, 5),
				new Word(Opcode.STD, 0, -1, 6),
				new Word(Opcode.STOP, -1, -1, -1)
		};

		public Word[] subtrai = new Word[] {
				new Word(Opcode.LDI, 0, -1, 50),
				new Word(Opcode.SUBI, 0, -1, 5),
				new Word(Opcode.STD, 0, -1, 5),
				new Word(Opcode.STOP, -1, -1, -1)
		};
	}
}