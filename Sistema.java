// PUCRS - Escola Politécnica - Sistemas Operacionais
// Prof. Fernando Dotti
// Código fornecido como parte da solução do projeto de Sistemas Operacionais
//
// VM
//    HW = memória, cpu
//    SW = tratamento int e chamada de sistema
// Funcionalidades de carga, execução e dump de memória

import java.io.Console;
import java.lang.reflect.Constructor;
import java.util.*;
import java.util.concurrent.Semaphore;

public class Sistema {	
	public static int CPU_TIMER_SLICE = 5;
	// -------------------------------------------------------------------------------------------------------
	// --------------------- H A R D W A R E - definicoes de HW ---------------------------------------------- 

	// -------------------------------------------------------------------------------------------------------
	// --------------------- M E M O R I A -  definicoes de palavra de memoria, memória ---------------------- 
	
	public class Memory {
		public int tamMem;
        public Word[] m;                  // m representa a memória fisica:   um array de posicoes de memoria (word)
	
		public Memory(int size){
			tamMem = size;
		    m = new Word[tamMem];  
		    for (int i=0; i<tamMem; i++) { m[i] = new Word(Opcode.___,-1,-1,-1); };
		}
		
		public void dump(Word w) {        // funcoes de DUMP nao existem em hardware - colocadas aqui para facilidade
						System.out.print("[ "); 
						System.out.print(w.opc); System.out.print(", ");
						System.out.print(w.r1);  System.out.print(", ");
						System.out.print(w.r2);  System.out.print(", ");
						System.out.print(w.p);  System.out.println("  ] ");
		}
		public void dump(int ini, int fim) {
			for (int i = ini; i < fim; i++) {
				System.out.print(i); System.out.print(":  ");  dump(m[i]);
			}
		}
    }
	
    // -------------------------------------------------------------------------------------------------------

	public class Word { 	// cada posicao da memoria tem uma instrucao (ou um dado)
		public Opcode opc; 	//
		public int r1; 		// indice do primeiro registrador da operacao (Rs ou Rd cfe opcode na tabela)
		public int r2; 		// indice do segundo registrador da operacao (Rc ou Rs cfe operacao)
		public int p; 		// parametro para instrucao (k ou A cfe operacao), ou o dado, se opcode = DADO

		public Word(Opcode _opc, int _r1, int _r2, int _p) {  // vide definição da VM - colunas vermelhas da tabela
			opc = _opc;   r1 = _r1;    r2 = _r2;	p = _p;
		}
	}
	
	// -------------------------------------------------------------------------------------------------------
    // --------------------- C P U  -  definicoes da CPU ----------------------------------------------------- 

	public enum Opcode {
		DATA, ___,		                    // se memoria nesta posicao tem um dado, usa DATA, se nao usada ee NULO ___
		JMP, JMPI, JMPIG, JMPIL, JMPIE,     // desvios e parada
		JMPIM, JMPIGM, JMPILM, JMPIEM, STOP, 
		JMPIGK, JMPILK, JMPIEK, JMPIGT,     
		ADDI, SUBI, ADD, SUB, MULT,         // matematicos
		LDI, LDD, STD, LDX, STX, MOVE,      // movimentacao
        TRAP                                // chamada de sistema
	}

	public enum Interrupts {               // possiveis interrupcoes que esta CPU gera
		noInterrupt, intEnderecoInvalido, intInstrucaoInvalida, intOverflow, intSTOP, intTimerSlice, intIOFinish;
	}

	public class ClockInterrupt {
		public CPU cpu;
		public int ticks;
		public ClockInterrupt() { 
			ticks = 0; 
		}

		public void _setCpu(CPU _cpu) {
			this.cpu = _cpu;
		}

		public void tick() { 
			ticks++;
			if (ticks % CPU_TIMER_SLICE == 0) {
				vm.cpu.interruptCPU(Interrupts.intTimerSlice);
				ticks = 0;
			}
		}
	}

	public class CPU extends Thread {
		private int maxInt; // valores maximo e minimo para inteiros nesta cpu
		private int minInt;
							// característica do processador: contexto da CPU ...
		private int pc; 			// ... composto de program counter,
		private Word ir; 			// instruction register,
		private int[] reg;       	// registradores da CPU

		private Semaphore irptSemaphore = new Semaphore(1); // semaforo para sincronizar CPU e tratador de interrupcoes
		// private Interrupts irpt; 	// durante instrucao, interrupcao pode ser sinalizada
		private List<Interrupts> irpt; 	// durante instrucao, interrupcao pode ser sinalizada

		// private int irptPid; 		// pid do processo que gerou a interrupcao
		private List<Integer> irptPid; 		// pid do processo que gerou a interrupcao

		private int base;   		// base e limite de acesso na memoria
		private int limite; // por enquanto toda memoria pode ser acessada pelo processo rodando
							// ATE AQUI: contexto da CPU - tudo que precisa sobre o estado de um processo para executa-lo
							// nas proximas versoes isto pode modificar
		public int pid;
		private Pages pages;

		private ClockInterrupt clock;

		private Memory mem;               // mem tem funcoes de dump e o array m de memória 'fisica' 
		private Word[] m;                 // CPU acessa MEMORIA, guarda referencia a 'm'. m nao muda. semre será um array de palavras

		private InterruptHandling ih;     // significa desvio para rotinas de tratamento de  Int - se int ligada, desvia
        private SysCallHandling sysCall;  // significa desvio para tratamento de chamadas de sistema - trap 
		private boolean debug;            // se true entao mostra cada instrucao em execucao
						
		public CPU(Memory _mem, InterruptHandling _ih, SysCallHandling _sysCall, boolean _debug, ClockInterrupt _clock) {     // ref a MEMORIA e interrupt handler passada na criacao da CPU
			maxInt =  32767;        // capacidade de representacao modelada
			minInt = -32767;        // se exceder deve gerar interrupcao de overflow
			mem = _mem;	            // usa mem para acessar funcoes auxiliares (dump)
			m = mem.m; 				// usa o atributo 'm' para acessar a memoria.
			reg = new int[10]; 		// aloca o espaço dos registradores - regs 8 e 9 usados somente para IO
			ih = _ih;               // aponta para rotinas de tratamento de int
            sysCall = _sysCall;     // aponta para rotinas de tratamento de chamadas de sistema
			debug =  _debug;        // se true, print da instrucao em execucao
			clock = _clock;
			clock._setCpu(this);
			irpt = new ArrayList<Interrupts>();
			irptPid = new ArrayList<Integer>();
		}
		
		private boolean legal(int e) {                    
			if (!pages.Validade(e)) {
				             // se endereco invalido, registra interrupcao
				vm.cpu.interruptCPU(Interrupts.intEnderecoInvalido);
				return false;
			}
			return true;
		}

		public int translateAddress(int e) {
			return pages.getPyhsicalAddress(e);
		}

		private boolean testOverflow(int v) {                       // toda operacao matematica deve avaliar se ocorre overflow                      
			if ((v < minInt) || (v > maxInt)) {                           
				vm.cpu.interruptCPU(Interrupts.intOverflow);        
				return false;
			};
			return true;
		}
		
		// public void setContext(int _base, int _limite, int _pc, Pages _pages) {  // no futuro esta funcao vai ter que ser 
		// 	base = _base;                                          // expandida para setar todo contexto de execucao,
		// 	limite = _limite;									   // agora,  setamos somente os registradores base,
		// 	pc = _pc;                                              // limite e pc (deve ser zero nesta versao)
		// 	irpt = Interrupts.noInterrupt;                         // reset da interrupcao registrada
		// 	pages = _pages;
		// }

		public void setContext(Process p) {
			base = 0;
			limite = m.length - 1;
			pc = p.pcb.pc;
			// irpt = Interrupts.noInterrupt; // TODO: VALIDAR ISSO
			pages = p.pages;
			pid = p.pid;
			reg = p.pcb.reg;
		}

		public void interruptCPU(Interrupts _irpt, int _pid) {          // interrupcao gerada por outro processo
			try {
				irptSemaphore.acquire();
			} catch (InterruptedException e) {
			}
			// irpt = _irpt;                                           // salva tipo de interrupcao e pid do processo que gerou
			// irptPid = _pid;                                         // a interrupcao
			irpt.add(_irpt);
			irptPid.add(_pid);

			irptSemaphore.release();
		}

		public void interruptCPU(Interrupts _irpt) {
			interruptCPU(_irpt, pid);
		}
		
		public void run() { 		// execucao da CPU supoe que o contexto da CPU, vide acima, esta devidamente setado			
			while (true) { 			// ciclo de instrucoes. acaba cfe instrucao, veja cada caso.
			   // --------------------------------------------------------------------------------------------------
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {}
				// Incrementa clock
				clock.tick();

			    // se running.size() == 0, náo ha processo rodando na CPU, neste caso 
				// apenas verifica interupção.
			   if (running.size() > 0) {
	
				   // FETCH
					if (legal(pc)) { 	// pc valido
						ir = m[pages.getPyhsicalAddress(pc)]; 	// <<<<<<<<<<<<           busca posicao da memoria apontada por pc, guarda em ir
						if (debug) { System.out.print("                               PID: "+ pid +" pc: "+pc+"       exec: ");  mem.dump(ir); }
						// clock.tick();
				   // --------------------------------------------------------------------------------------------------
				   // EXECUTA INSTRUCAO NO ir
						switch (ir.opc) {   // conforme o opcode (código de operação) executa
	
						// Instrucoes de Busca e Armazenamento em Memoria
							case LDI: // Rd ← k
								reg[ir.r1] = ir.p;
								pc++;
								break;
	
							case LDD: // Rd <- [A]
								if (legal(ir.p)) {
								   reg[ir.r1] = m[pages.getPyhsicalAddress(ir.p)].p;
								   pc++;
								}
								break;
	
							case LDX: // RD <- [RS] // NOVA
								if (legal(reg[ir.r2])) {
									reg[ir.r1] = m[pages.getPyhsicalAddress(reg[ir.r2])].p;
									pc++;
								}
								break;
	
							case STD: // [A] ← Rs
								if (legal(ir.p)) {
									m[pages.getPyhsicalAddress(ir.p)].opc = Opcode.DATA;
									m[pages.getPyhsicalAddress(ir.p)].p = reg[ir.r1];
									pc++;
								};
								break;
	
							case STX: // [Rd] ←Rs
								if (legal(reg[ir.r1])) {
									m[pages.getPyhsicalAddress(reg[ir.r1])].opc = Opcode.DATA;      
									m[pages.getPyhsicalAddress(reg[ir.r1])].p = reg[ir.r2];          
									pc++;
								};
								break;
							
							case MOVE: // RD <- RS
								reg[ir.r1] = reg[ir.r2];
								pc++;
								break;	
								
						// Instrucoes Aritmeticas
							case ADD: // Rd ← Rd + Rs
								reg[ir.r1] = reg[ir.r1] + reg[ir.r2];
								testOverflow(reg[ir.r1]);
								pc++;
								break;
	
							case ADDI: // Rd ← Rd + k
								reg[ir.r1] = reg[ir.r1] + ir.p;
								testOverflow(reg[ir.r1]);
								pc++;
								break;
	
							case SUB: // Rd ← Rd - Rs
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
							
							case JMPIG: // If Rc > 0 Then PC ← Rs Else PC ← PC +1
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
									 pc = m[pages.getPyhsicalAddress(ir.p)].p;
								 break; 
		
							case JMPIGM: // If RC > 0 then PC <- [A] else PC++
									 if (reg[ir.r2] > 0) {
										pc = m[pages.getPyhsicalAddress(ir.p)].p;
									} else {
										pc++;
									}
								 break;  
		
							case JMPILM: // If RC < 0 then PC <- k else PC++
									 if (reg[ir.r2] < 0) {
										pc = m[pages.getPyhsicalAddress(ir.p)].p;
									} else {
										pc++;
									}
								 break; 
		
							case JMPIEM: // If RC = 0 then PC <- k else PC++
									if (reg[ir.r2] == 0) {
										pc = m[pages.getPyhsicalAddress(ir.p)].p;
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
								vm.cpu.interruptCPU(Interrupts.intSTOP);
								break;
	
							case DATA:
								vm.cpu.interruptCPU(Interrupts.intInstrucaoInvalida);
								break;
	
						// Chamada de sistema
							case TRAP:
								pc++;
								sysCall.handle();            // <<<<< aqui desvia para rotina de chamada de sistema, no momento so temos IO
								 break;
	
						// Inexistente
							default:
								vm.cpu.interruptCPU(Interrupts.intInstrucaoInvalida);
								break;
						}
					}
			   }
			   // --------------------------------------------------------------------------------------------------
			   // VERIFICA INTERRUPÇÃO !!! - TERCEIRA FASE DO CICLO DE INSTRUÇÕES
				// if (!(irpt == Interrupts.noInterrupt)) {   // existe interrupção
				// 	ih.handle(irpt,pc);                   // desvia para rotina de tratamento
				// 	irpt = Interrupts.noInterrupt;
				// 	irptSemaphore.release();
				// }
				try {
					irptSemaphore.acquire();
				} catch (InterruptedException e) {
				}
				while (!irpt.isEmpty()) {
					ih.handle(irpt.remove(0),pc, irptPid.remove(0));                   // desvia para rotina de tratamento
				}
				irptSemaphore.release();
			}  // FIM DO CICLO DE UMA INSTRUÇÃO
		}      
	}
    // ------------------ C P U - fim ------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------

    
	
    // ------------------- V M  - constituida de CPU e MEMORIA -----------------------------------------------
    // -------------------------- atributos e construcao da VM -----------------------------------------------
	public class VM {
		public int tamMem;    
        public Word[] m;  
		public Memory mem;   
        public CPU cpu;
		public ClockInterrupt clock;
		
        public VM(InterruptHandling ih, SysCallHandling sysCall){   
		 // vm deve ser configurada com endereço de tratamento de interrupcoes e de chamadas de sistema
	     // cria memória
		     tamMem = 1024;
  		 	 mem = new Memory(tamMem);
			 m = mem.m;
	  	 // cria cpu
			
			clock = new ClockInterrupt();
			
			cpu = new CPU(mem,ih,sysCall, true, clock);                   // true liga debug
		}
	}
    // ------------------- V M  - fim ------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------

    // --------------------H A R D W A R E - fim -------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------------

	// -------------------------------------------------------------------------------------------------------
	
	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// -------------------------------------------------------------------------------------------------------
	// ------------------- S O F T W A R E - inicio ----------------------------------------------------------

	public class Escalonador {
		public Semaphore semaforo = new Semaphore(1);

		public boolean hasProcess() {
			return ready.size() > 0 || blocked.size() > 0;
		}

		public void getNextProcess() {
			// while (true) {
			// 	if (ready.size() > 0) {
			// 		break;
			// 	}
			// 	// System.out.println("Escalonador: Nao ha processos prontos, esperando");
			// 	try {
			// 		Thread.sleep(500);
			// 	} catch (InterruptedException e) {
			// 	}
			// }
			if (ready.size() == 0 && running.size() == 0) {
				vm.cpu.pid = -1;
				return;
			}
			// try {
			// 	semaforo.acquire();
			// } catch (InterruptedException e) {
			// }

			Process next = ready.get(0);
			if (vm.cpu.debug) System.out.println("Colocando processo " + next.pid + " na CPU");
			next.moveToRunning();
			next.restoreStatus();
		}
	}

	// ------------------- I N T E R R U P C O E S  - rotinas de tratamento ----------------------------------
    public class InterruptHandling {
            public void handle(Interrupts irpt, int pc, int pid) {   // apenas avisa - todas interrupcoes neste momento finalizam o programa
				if (irpt != Interrupts.intTimerSlice || running.size() > 0) System.out.println("                                               Interrupcao "+ irpt+ "   pc: "+pc + " PID: " + pid);
				if (irpt == Interrupts.intSTOP) {
					if (vm.cpu.debug) System.out.println("Processo finalizado: " + pid);
					killProcess(pid); // kill and free process

					// escalonador.getNextProcess();
					return;
				} else if (irpt == Interrupts.intTimerSlice) {
					if (pid <= 0) {
						escalonador.getNextProcess();
					}
					Process p = getProcessFromRunning(pid);

					if (p == null) {
						return;
					}

					if (vm.cpu.debug) System.out.println("Removendo processo " + pid + " da CPU; Colocando na fila de pronto");
					p.saveStatus();
					// Move to ready
					p.moveToReady();

					escalonador.getNextProcess();
					return;
				} else if (irpt == Interrupts.intIOFinish) {
					System.out.println("IO finalizado, colocando processo "+ pid +" na fila de pronto");
					Process p = getProcess(pid);
					p.moveToReady();
					return;
				}
				
				System.out.println("Interupção não tratada, finalizando processo: " + pid);
				killProcess(pid);
				escalonador.getNextProcess();
			}
	}

    // ------------------- C H A M A D A S  D E  S I S T E M A  - rotinas de tratamento ----------------------
    public class SysCallHandling {
        private VM vm;
        public void setVM(VM _vm){
            vm = _vm;
        }
        public void handle() {   // apenas avisa - todas interrupcoes neste momento finalizam o programa
            System.out.println("                                               Chamada de Sistema com op  /  par:  "+ vm.cpu.reg[8] + " / " + vm.cpu.reg[9]);
			// Read and save registers
			int op = vm.cpu.reg[8];
			int addressLogic = vm.cpu.reg[9];
			int addressPhysical = vm.cpu.translateAddress(addressLogic);
			// System.out.println("                                               Endereco logico: " + addressLogic + " / Endereco fisico: " + addressPhysical);
			Process p = getProcess(vm.cpu.pid);
			switch (op) {
				case 1:
					console.addRequest(new IORequest(p.pid, addressPhysical, op));
					// vm.cpu.interruptCPU(Interrupts.intIO); // TODO: Fazer funcionar interupcao
					p.saveStatus();
					p.moveToBlocked();
					escalonador.getNextProcess();


					
					break;
				case 2:
					// System.out.println("OUT: " + vm.cpu.m[addressPhysical].p);
					console.addRequest(new IORequest(vm.cpu.pid, addressPhysical, op));
					// vm.cpu.interruptCPU(Interrupts.intIO);
					p.saveStatus();
					p.moveToBlocked();
					escalonador.getNextProcess();

					break;
				default:
					System.out.println("Chamada de sistema não tratada, finalizando processo: " + vm.cpu.pid);
					vm.cpu.interruptCPU(Interrupts.intSTOP);
					break;
			}
		}
    }

	class IORequest {
		public int pid;
		public int address;
		public int op;
		public IORequest(int _pid, int _address, int _op) {
			pid = _pid;
			address = _address;
			op = _op;
		}
	}
	class Console extends Thread {
		public LinkedList<IORequest> requests;

		public Console() {
			requests = new LinkedList<IORequest>();
		}

		public void addRequest(IORequest request) {
			requests.add(request);
		}

		public void requestInput() {
			
		}

		@Override
		public void run() {
			while (true){
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					System.out.println("timer over");
				}
				if (requests.size() > 0) {
					
					IORequest request = requests.removeFirst();

					try{
						shell.sem.acquire();
					} catch (InterruptedException e) {
						System.out.println("timer over");
					}

					if (vm.cpu.debug) System.out.println("IO Request: PID: " + request.pid + " / Adress: " + request.address + " / OP: " + request.op);
					switch (request.op) {
						case 1:
							Scanner ler = new Scanner(System.in);
							System.out.println("[PID: " + request.pid + "] Digite um valor: ");
							vm.mem.m[request.address] = new Word(Opcode.DATA, -1, -1, ler.nextInt());
							ler.nextLine();
							break;
						case 2:
							System.out.println("[PID: " + request.pid + "] OUT: " + vm.mem.m[request.address].p);
							break;
						default:
							break;
					}

					vm.cpu.interruptCPU(Interrupts.intIOFinish, request.pid);
					shell.sem.release();
				}
			}
		}
	}

    // ------------------ U T I L I T A R I O S   D O   S I S T E M A -----------------------------------------
	// ------------------ load é invocado a partir de requisição do usuário

	// private void loadProgram(Word[] p, Word[] m) {
	// 	for (int i = 0; i < p.length; i++) {
	// 		m[i].opc = p[i].opc;     m[i].r1 = p[i].r1;     m[i].r2 = p[i].r2;     m[i].p = p[i].p;
	// 	}
	// }

	// public boolean aloca(int tam, int[] out) {
	// 	return false;
	// }

	// private void loadProgram(Word[] p) {
	// 	// aloca(p.length, out);
	// 	// loadProgram(p, out);
		
	// 	loadProgram(p, vm.m);
	// }

	// private void loadAndExec(Word[] p){
	// 	loadProgram(p);    // carga do programa na memoria
	// 			System.out.println("---------------------------------- programa carregado na memoria");
	// 			vm.mem.dump(0, p.length);            // dump da memoria nestas posicoes				
	// 	vm.cpu.setContext(0, vm.tamMem - 1, 0);      // seta estado da cpu ]
	// 			System.out.println("---------------------------------- inicia execucao ");
	// 	vm.cpu.run();                                // cpu roda programa ate parar	
	// 			System.out.println("---------------------------------- memoria após execucao ");
	// 			vm.mem.dump(0, p.length);            // dump da memoria com resultado
	// }


	// -------------------------------------------------------------------------------------------------------
    // -------------------  S I S T E M A --------------------------------------------------------------------

	public boolean criaProcesso(Word[] p) {
		Process proc = new Process();

		boolean response = gm.aloc(p.length, proc.pages);

		if (!response) {
			return false;
		}
		proc.pid = ++lastPid;

		proc.load(p);
		// proc.state = ProcessState.ready;
		ready.add(proc);
		return true;
	}

	public VM vm;
	public InterruptHandling ih;
	public SysCallHandling sysCall;
	public static Programas progs;
	public GM gm; 

	public List<Process> ready, running, blocked ; //finished;
	public int lastPid = 0;

	public Escalonador escalonador;
	public Console console;
	public Shell shell;

	public class GM {
		int tamFrame;
		int tamPage;
		Frame[] frames;
		List<Integer> usedFrames;
		Memory mem;

		public GM(Memory _mem, int tamFrame, int tamMem) {
			this.tamFrame = tamFrame;
			this.tamPage = tamFrame;
			this.mem = _mem;
			
			initializeFrames(tamFrame, tamMem);
			this.usedFrames = new ArrayList<Integer>(tamMem / tamFrame);
		}

		private void initializeFrames(int tam, int tamMem) {
			this.frames = new Frame[tamMem / tam];
			for (int i = 0; i < this.frames.length; i++) {
				this.frames[i] = new Frame(tam);
				int base = i * tam;
				this.frames[i].base = base;
				for (int j = 0; j < tam; j++) {
					this.frames[i].addresses[j] = base + j;
					// this.frames[i].addresses[j] = mem.m[base + j];
				}
			}
		}

		public boolean aloc(int tam, Pages pagesOut) {
			int need = (int) Math.ceil(tam / (tamFrame * 1.0));

			if (need <= frames.length - usedFrames.size()) {
				int count = 0;
				pagesOut.frames = new Frame[need];
				pagesOut.framesAddresses = new int[need];

				for (int i = 0; i < frames.length; i++) {
					if (!usedFrames.contains(i)) {
						usedFrames.add(i);
						// framesOut[count] = i;
						pagesOut.frames[count] = frames[i];
						pagesOut.framesAddresses[count] = i;
						count++;
						if (count == need) {
							return true;
						}
					}
				}
			}

			return false;
		}

		public void free(int[] frames) {
			for (int i = 0; i < frames.length; i++) {
				usedFrames.remove((Integer) frames[i]);
			}
		}
	}

	class Frame {
		int[] addresses;
		int base;

		public Frame(int tam) {
			this.addresses = new int[tam];
		}
	}

	class Pages {
		Frame[] frames;
		int[] framesAddresses;

		public Pages() {
		}

		public int[] getAddresses() {
			int[] addresses = new int[frames.length * frames[0].addresses.length];
			int count = 0;
			for (int i = 0; i < frames.length; i++) {
				for (int j = 0; j < frames[i].addresses.length; j++) {
					addresses[count] = frames[i].addresses[j];
					count++;
				}
			}

			return addresses;
		}

		public int getPyhsicalAddress(int virtualAddress) {
			int frame = virtualAddress / gm.tamFrame;
			int offset = virtualAddress % gm.tamFrame;
			int physicalAddress;

			// System.out.println("frame: " + frame + " offset: " + offset + " virtual: " + virtualAddress);
			try {
				physicalAddress = frames[frame].addresses[offset];

			} catch (IndexOutOfBoundsException e) {
				System.out.println("frame: " + frame + " offset: " + offset + " virtual: " + virtualAddress);
				return -1;
			}

			return physicalAddress;
		}

		public boolean Validade(int virtualAddress) {
			return virtualAddress < frames.length * gm.tamFrame && virtualAddress >= 0;
		}
	}
	class Process {
		int pid;
		Pages pages;
		PCB pcb;

		class PCB {
			int pc;
			int[] reg;

			public PCB() {
				this.pc = 0;
				this.reg = new int[10];
			}
		}

		public Process() {
			this.pcb = new PCB();
			this.pages = new Pages();

		}

		public void saveStatus() {
			this.pcb.pc = vm.cpu.pc;
			this.pcb.reg = vm.cpu.reg;
		}

		public void restoreStatus() {
			vm.cpu.setContext(this);
		}

		public void load(Word[] p) {
			int[] addresses = pages.getAddresses();
			for (int i = 0; i < p.length; i++) {
				vm.m[addresses[i]] = p[i];
			}
		}

		public void free() {
			gm.free(pages.framesAddresses);
		}

		public void moveToReady() {
			ready.add(this);
			running.remove(this);
			blocked.remove(this);
		}

		public void moveToRunning() {
			running.add(this);
			ready.remove(this);
		}

		public void moveToBlocked() {
			if (vm.cpu.debug) System.out.println("Colocando processo " + pid + " na fila de bloqueado");
			running.remove(this);
			blocked.add(this);
			
		}
	}

	public Process getProcessFromBlocked(int pid) {
		for (Process p : blocked) {
			if (p.pid == pid) {
				return p;
			}
		}

		return null;
	}

	public Process getProcessFromReady(int pid) {
		for (Process p : ready) {
			if (p.pid == pid) {
				return p;
			}
		}

		return null;
	}

	public Process getProcessFromRunning(int pid) {
		for (Process p : running) {
			if (p.pid == pid) {
				return p;
			}
		}

		return null;
	}

	public Process getProcess(int pid) {
		Process p = getProcessFromReady(pid);
		if (p != null) {
			return p;
		}

		p = getProcessFromRunning(pid);
		if (p != null) {
			return p;
		}

		return getProcessFromBlocked(pid);
	}	


    public Sistema(){   // a VM com tratamento de interrupções
		 ih = new InterruptHandling();
         sysCall = new SysCallHandling();
		 vm = new VM(ih, sysCall);
		 sysCall.setVM(vm);
		 progs = new Programas();
		 gm = new GM(vm.mem ,8, vm.tamMem);

		 ready = new ArrayList<Process>();
		 running = new ArrayList<Process>();
		 blocked = new ArrayList<Process>();

		 shell = new Shell();
		 console = new Console();
		 escalonador = new Escalonador();
	}

    // -------------------  S I S T E M A - fim --------------------------------------------------------------
    // -------------------------------------------------------------------------------------------------------

    // -------------------------------------------------------------------------------------------------------
    // ------------------- instancia e testa sistema
	public static void main(String args[]) {
		Sistema s = new Sistema();			
		//s.loadAndExec(progs.fibonacci10);
		//s.loadAndExec(progs.progMinimo);
		// s.loadAndExec(progs.fatorial);
		//s.loadAndExec(progs.fatorialTRAP); // saida
		//s.loadAndExec(progs.fibonacciTRAP); // entrada
		//s.loadAndExec(progs.PC); // bubble sort
		//s.loadAndExec(progs.testeInput);
		// s.loadAndExec(progs.testeOutput);

		s.criaProcesso(progs.fatorial);
		s.criaProcesso(progs.fibonacci10);
		s.criaProcesso(progs.testeOutput);
		s.criaProcesso(progs.testeInput);
		// s.criaProcesso(progs.testeOutput);


		
		// s.ps();
		// s.dumpProcess(2);
		// s.killProcess(1);
		// s.ps();
		// s.traceOff();
		// s.executaProcesso(2);
		// s.ps();

		// s.criaProcesso(progs.fatorial);
		// s.ps();
		// s.dumpProcess(3);

		// s.dumpMemory(0, 10);

		// s.terminal();
		// s.escalonador.start();
		s.shell.start();
		s.console.start();
	}
	public boolean cpuIsRunning = false;
	public void executaTudo() {
		if (cpuIsRunning) {
			System.out.println("CPU já está em execução");
			return;
		}
		escalonador.getNextProcess();
		vm.cpu.start();
		cpuIsRunning = true;
	}

	public boolean killProcess(int pid) { // Testar matar processo em execução
		Process p = getProcess(pid);

		if (p != null) {
			ready.remove(p);
			running.remove(p);
			blocked.remove(p);
			p.free();

			if (p.pid == vm.cpu.pid) {
				escalonador.getNextProcess();
			}

			return true;
		} else {
			System.out.println("Processo não encontrado na filas");
			return false;
		}
	}
	public class Shell extends Thread {

		public Semaphore sem = new Semaphore(1);

		public Shell() {
		}

		public void ps() {
			System.out.println("PID\t\tEstado");
			for (int i = 0; i < ready.size(); i++) {
				System.out.println(ready.get(i).pid + "\t\tReady");
			}

			for (int i = 0; i < running.size(); i++) {
				System.out.println(running.get(i).pid + "\t\tRunning");
			}

			for (int i = 0; i < blocked.size(); i++) {
				System.out.println(blocked.get(i).pid + "\t\tBlocked");
			}
		}

		public void dumpProcess(int pid) {
			Process p = getProcess(pid);

			if (p != null) {
				System.out.println("PID: " + p.pid);
				System.out.println("PC: " + p.pcb.pc);
				// System.out.println("SP: " + p.pcb.sp);
				System.out.println("Reg: " + Arrays.toString(p.pcb.reg));
				System.out.println("Frames: " + Arrays.toString(p.pages.framesAddresses));
				System.out.println("Address Pyhsical: " + Arrays.toString(p.pages.getAddresses()));
			} else {
				System.out.println("Processo não encontrado na fila de ready");
			}
		}

		public void dumpMemory(int start, int end) {
			vm.mem.dump(start, end);
		}

		public void traceOn() {
			vm.cpu.debug = true;
		}

		public void traceOff() {
			vm.cpu.debug = false;
		}

		public void exit() {
			System.exit(0);
		}

		public void terminal() {
			Scanner sc = new Scanner(System.in);
			String cmd = "";
			while (true) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}

				try{
					sem.acquire();
				} catch (InterruptedException e) {
					// e.printStackTrace();
				}

				System.out.print(">> ");
				cmd = sc.nextLine();
				cmd = cmd.toLowerCase();
				cmd = cmd.trim();
				if (cmd.equals("exit")) {
					break;

				} else if (cmd.equals("ps")) {
					ps();

				} else if (cmd.equals("trace on")) {
					traceOn();

				} else if (cmd.equals("trace off")) {
					traceOff();

				} else if (cmd.equals("dump memory")) {
					System.out.print("Start: ");
					int start = sc.nextInt();
					System.out.print("End: ");
					int end = sc.nextInt();
					dumpMemory(start, end);
					sc.nextLine();

				} else if (cmd.equals("dump process")) {
					System.out.print("PID: ");
					int pid = sc.nextInt();
					dumpProcess(pid);
					sc.nextLine();

				} else if (cmd.equals("kill")) {
					System.out.print("PID: ");
					int pid = sc.nextInt();
					killProcess(pid);
					sc.nextLine();

				// } else if (cmd.equals("exec")) {
				// 	System.out.print("PID: ");
				// 	int pid = sc.nextInt();
				// 	executaProcesso(pid);
				// 	sc.nextLine();
				} else if (cmd.equals("execall")) {
					executaTudo();

				} else if (cmd.equals("load")) {
					System.out.print("Programa: ");
					String prog = sc.nextLine();
					if (prog.equals("fibonacci10")) {
						criaProcesso(progs.fibonacci10);

					} else if (prog.equals("fatorial")) {
						criaProcesso(progs.fatorial);

					} else if (prog.equals("fatorialTRAP")) {
						criaProcesso(progs.fatorialTRAP);

					} else if (prog.equals("fibonacciTRAP")) {
						criaProcesso(progs.fibonacciTRAP);

					} else if (prog.equals("PC")) {
						criaProcesso(progs.PC);

					} else if (prog.equals("testeInput")) {
						criaProcesso(progs.testeInput);

					} else if (prog.equals("testeOutput")) {
						criaProcesso(progs.testeOutput);

					} else if (prog.equals("progMinimo")) {
						criaProcesso(progs.progMinimo);

					} else {
						System.out.println("Programa não encontrado");
					}

				} else if (cmd.equals("help")) {
					System.out.println("Comandos:");
					System.out.println("help");
					System.out.println("load");
					// System.out.println("exec");
					System.out.println("execall");
					System.out.println("ps");
					System.out.println("kill");
					System.out.println("dump memory");
					System.out.println("dump process");
					System.out.println("trace on");
					System.out.println("trace off");
					System.out.println("exit");
				} else {
					System.out.println("Comando não encontrado");
				}

				sem.release();
			}

			sc.close();
			exit();
		}

		@Override
		public void run() {
			terminal();
		}
	}

   // -------------------------------------------------------------------------------------------------------
   // -------------------------------------------------------------------------------------------------------
   // -------------------------------------------------------------------------------------------------------
   // --------------- P R O G R A M A S  - não fazem parte do sistema
   // esta classe representa programas armazenados (como se estivessem em disco) 
   // que podem ser carregados para a memória (load faz isto)

   public class Programas {
		public Word[] testeInput = new Word[] {
			new Word(Opcode.LDI, 8, -1, 1), // r8 = input
			new Word(Opcode.LDI, 9, -1, 4), // r9 = input
			new Word(Opcode.TRAP, -1, -1, -1),
			new Word(Opcode.STOP, -1, -1, -1),
			new Word(Opcode.DATA, -1, -1, -1)};

		public Word[] testeOutput = new Word[] {
			new Word(Opcode.LDI, 0, -1, 999), // 
			new Word(Opcode.STD, 0, -1, 7), // 
			new Word(Opcode.LDI, 8, -1, 2), // 
			new Word(Opcode.LDI, 9, -1,7), // 
			new Word(Opcode.TRAP, -1, -1, -1),
			new Word(Opcode.STOP, -1, -1, -1),
			new Word(Opcode.DATA, -1, -1, -1)};

	   public Word[] fatorial = new Word[] {
	 	           // este fatorial so aceita valores positivos.   nao pode ser zero
	 											 // linha   coment
	 		new Word(Opcode.LDI, 0, -1, 4),      // 0   	r0 é valor a calcular fatorial
	 		new Word(Opcode.LDI, 1, -1, 1),      // 1   	r1 é 1 para multiplicar (por r0)
	 		new Word(Opcode.LDI, 6, -1, 1),      // 2   	r6 é 1 para ser o decremento
	 		new Word(Opcode.LDI, 7, -1, 8),      // 3   	r7 tem posicao de stop do programa = 8
	 		new Word(Opcode.JMPIE, 7, 0, 0),     // 4   	se r0=0 pula para r7(=8)
			new Word(Opcode.MULT, 1, 0, -1),     // 5   	r1 = r1 * r0
	 		new Word(Opcode.SUB, 0, 6, -1),      // 6   	decrementa r0 1 
	 		new Word(Opcode.JMP, -1, -1, 4),     // 7   	vai p posicao 4
	 		new Word(Opcode.STD, 1, -1, 10),     // 8   	coloca valor de r1 na posição 10
	 		new Word(Opcode.STOP, -1, -1, -1),   // 9   	stop
	 		new Word(Opcode.DATA, -1, -1, -1) }; // 10   ao final o valor do fatorial estará na posição 10 da memória                                    
		
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
			new Word(Opcode.DATA, -1, -1, -1),   // POS 20
			new Word(Opcode.DATA, -1, -1, -1),
			new Word(Opcode.DATA, -1, -1, -1),
			new Word(Opcode.DATA, -1, -1, -1),
			new Word(Opcode.DATA, -1, -1, -1),
			new Word(Opcode.DATA, -1, -1, -1),
			new Word(Opcode.DATA, -1, -1, -1),
			new Word(Opcode.DATA, -1, -1, -1),
			new Word(Opcode.DATA, -1, -1, -1),
			new Word(Opcode.DATA, -1, -1, -1) }; // ate aqui - serie de fibonacci ficara armazenada
		
       public Word[] fatorialTRAP = new Word[] {
		   new Word(Opcode.LDI, 0, -1, 7),// numero para colocar na memoria
		   new Word(Opcode.STD, 0, -1, 50),
		   new Word(Opcode.LDD, 0, -1, 50),
		   new Word(Opcode.LDI, 1, -1, -1),
		   new Word(Opcode.LDI, 2, -1, 13),// SALVAR POS STOP
           new Word(Opcode.JMPIL, 2, 0, -1),// caso negativo pula pro STD
           new Word(Opcode.LDI, 1, -1, 1),
           new Word(Opcode.LDI, 6, -1, 1),
           new Word(Opcode.LDI, 7, -1, 13),
           new Word(Opcode.JMPIE, 7, 0, 0), //POS 9 pula pra STD (Stop-1)
           new Word(Opcode.MULT, 1, 0, -1),
           new Word(Opcode.SUB, 0, 6, -1),
           new Word(Opcode.JMP, -1, -1, 9),// pula para o JMPIE
           new Word(Opcode.STD, 1, -1, 18),
           new Word(Opcode.LDI, 8, -1, 2),// escrita
           new Word(Opcode.LDI, 9, -1, 18),//endereco com valor a escrever
           new Word(Opcode.TRAP, -1, -1, -1),
           new Word(Opcode.STOP, -1, -1, -1), // POS 17
           new Word(Opcode.DATA, -1, -1, -1)  };//POS 18	
		   
	       public Word[] fibonacciTRAP = new Word[] { // mesmo que prog exemplo, so que usa r0 no lugar de r8
			new Word(Opcode.LDI, 8, -1, 1),// leitura
			new Word(Opcode.LDI, 9, -1, 100),//endereco a guardar
			new Word(Opcode.TRAP, -1, -1, -1),
			new Word(Opcode.LDD, 7, -1, 100),// numero do tamanho do fib
			new Word(Opcode.LDI, 3, -1, 0),
			new Word(Opcode.ADD, 3, 7, -1),
			new Word(Opcode.LDI, 4, -1, 36),//posicao para qual ira pular (stop) *
			new Word(Opcode.LDI, 1, -1, -1),// caso negativo
			new Word(Opcode.STD, 1, -1, 41),
			new Word(Opcode.JMPIL, 4, 7, -1),//pula pra stop caso negativo *
			new Word(Opcode.JMPIE, 4, 7, -1),//pula pra stop caso 0
			new Word(Opcode.ADDI, 7, -1, 41),// fibonacci + posição do stop
			new Word(Opcode.LDI, 1, -1, 0),
			new Word(Opcode.STD, 1, -1, 41),    // 25 posicao de memoria onde inicia a serie de fibonacci gerada
			new Word(Opcode.SUBI, 3, -1, 1),// se 1 pula pro stop
			new Word(Opcode.JMPIE, 4, 3, -1),
			new Word(Opcode.ADDI, 3, -1, 1),
			new Word(Opcode.LDI, 2, -1, 1),
			new Word(Opcode.STD, 2, -1, 42),
			new Word(Opcode.SUBI, 3, -1, 2),// se 2 pula pro stop
			new Word(Opcode.JMPIE, 4, 3, -1),
			new Word(Opcode.LDI, 0, -1, 43),
			new Word(Opcode.LDI, 6, -1, 25),// salva posição de retorno do loop
			new Word(Opcode.LDI, 5, -1, 0),//salva tamanho
			new Word(Opcode.ADD, 5, 7, -1),
			new Word(Opcode.LDI, 7, -1, 0),//zera (inicio do loop)
			new Word(Opcode.ADD, 7, 5, -1),//recarrega tamanho
			new Word(Opcode.LDI, 3, -1, 0),
			new Word(Opcode.ADD, 3, 1, -1),
			new Word(Opcode.LDI, 1, -1, 0),
			new Word(Opcode.ADD, 1, 2, -1),
			new Word(Opcode.ADD, 2, 3, -1),
			new Word(Opcode.STX, 0, 2, -1),
			new Word(Opcode.ADDI, 0, -1, 1),
			new Word(Opcode.SUB, 7, 0, -1),
			new Word(Opcode.JMPIG, 6, 7, -1),//volta para o inicio do loop
			new Word(Opcode.STOP, -1, -1, -1),   // POS 36
			new Word(Opcode.DATA, -1, -1, -1),
			new Word(Opcode.DATA, -1, -1, -1),
			new Word(Opcode.DATA, -1, -1, -1),
			new Word(Opcode.DATA, -1, -1, -1),
			new Word(Opcode.DATA, -1, -1, -1),   // POS 41
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
		//dado um inteiro em alguma posição de memória,
		// se for negativo armazena -1 na saída; se for positivo responde o fatorial do número na saída
		new Word(Opcode.LDI, 0, -1, 7),// numero para colocar na memoria
		new Word(Opcode.STD, 0, -1, 50),
		new Word(Opcode.LDD, 0, -1, 50),
		new Word(Opcode.LDI, 1, -1, -1),
		new Word(Opcode.LDI, 2, -1, 13),// SALVAR POS STOP
		new Word(Opcode.JMPIL, 2, 0, -1),// caso negativo pula pro STD
		new Word(Opcode.LDI, 1, -1, 1),
		new Word(Opcode.LDI, 6, -1, 1),
		new Word(Opcode.LDI, 7, -1, 13),
		new Word(Opcode.JMPIE, 7, 0, 0), //POS 9 pula pra STD (Stop-1)
		new Word(Opcode.MULT, 1, 0, -1),
		new Word(Opcode.SUB, 0, 6, -1),
		new Word(Opcode.JMP, -1, -1, 9),// pula para o JMPIE
		new Word(Opcode.STD, 1, -1, 15),
		new Word(Opcode.STOP, -1, -1, -1), // POS 14
		new Word(Opcode.DATA, -1, -1, -1)}; //POS 15

public Word[] PC = new Word[] {
		//Para um N definido (10 por exemplo)
		//o programa ordena um vetor de N números em alguma posição de memória;
		//ordena usando bubble sort
		//loop ate que não swap nada
		//passando pelos N valores
		//faz swap de vizinhos se da esquerda maior que da direita
		new Word(Opcode.LDI, 7, -1, 5),// TAMANHO DO BUBBLE SORT (N)
		new Word(Opcode.LDI, 6, -1, 5),//aux N
		new Word(Opcode.LDI, 5, -1, 46),//LOCAL DA MEMORIA
		new Word(Opcode.LDI, 4, -1, 47),//aux local memoria
		new Word(Opcode.LDI, 0, -1, 4),//colocando valores na memoria
		new Word(Opcode.STD, 0, -1, 46),
		new Word(Opcode.LDI, 0, -1, 3),
		new Word(Opcode.STD, 0, -1, 47),
		new Word(Opcode.LDI, 0, -1, 5),
		new Word(Opcode.STD, 0, -1, 48),
		new Word(Opcode.LDI, 0, -1, 1),
		new Word(Opcode.STD, 0, -1, 49),
		new Word(Opcode.LDI, 0, -1, 2),
		new Word(Opcode.STD, 0, -1, 50),//colocando valores na memoria até aqui - POS 13
		new Word(Opcode.LDI, 3, -1, 25),// Posicao para pulo CHAVE 1
		new Word(Opcode.STD, 3, -1, 99),
		new Word(Opcode.LDI, 3, -1, 22),// Posicao para pulo CHAVE 2
		new Word(Opcode.STD, 3, -1, 98),
		new Word(Opcode.LDI, 3, -1, 38),// Posicao para pulo CHAVE 3
		new Word(Opcode.STD, 3, -1, 97),
		new Word(Opcode.LDI, 3, -1, 25),// Posicao para pulo CHAVE 4 (não usada)
		new Word(Opcode.STD, 3, -1, 96),
		new Word(Opcode.LDI, 6, -1, 0),//r6 = r7 - 1 POS 22
		new Word(Opcode.ADD, 6, 7, -1),
		new Word(Opcode.SUBI, 6, -1, 1),//ate aqui
		new Word(Opcode.JMPIEM, -1, 6, 97),//CHAVE 3 para pular quando r7 for 1 e r6 0 para interomper o loop de vez do programa
		new Word(Opcode.LDX, 0, 5, -1),//r0 e r1 pegando valores das posições da memoria POS 26
		new Word(Opcode.LDX, 1, 4, -1),
		new Word(Opcode.LDI, 2, -1, 0),
		new Word(Opcode.ADD, 2, 0, -1),
		new Word(Opcode.SUB, 2, 1, -1),
		new Word(Opcode.ADDI, 4, -1, 1),
		new Word(Opcode.SUBI, 6, -1, 1),
		new Word(Opcode.JMPILM, -1, 2, 99),//LOOP chave 1 caso neg procura prox
		new Word(Opcode.STX, 5, 1, -1),
		new Word(Opcode.SUBI, 4, -1, 1),
		new Word(Opcode.STX, 4, 0, -1),
		new Word(Opcode.ADDI, 4, -1, 1),
		new Word(Opcode.JMPIGM, -1, 6, 99),//LOOP chave 1 POS 38
		new Word(Opcode.ADDI, 5, -1, 1),
		new Word(Opcode.SUBI, 7, -1, 1),
		new Word(Opcode.LDI, 4, -1, 0),//r4 = r5 + 1 POS 41
		new Word(Opcode.ADD, 4, 5, -1),
		new Word(Opcode.ADDI, 4, -1, 1),//ate aqui
		new Word(Opcode.JMPIGM, -1, 7, 98),//LOOP chave 2
		new Word(Opcode.STOP, -1, -1, -1), // POS 45
		new Word(Opcode.DATA, -1, -1, -1),
		new Word(Opcode.DATA, -1, -1, -1),
		new Word(Opcode.DATA, -1, -1, -1),
		new Word(Opcode.DATA, -1, -1, -1),
		new Word(Opcode.DATA, -1, -1, -1),
		new Word(Opcode.DATA, -1, -1, -1),
		new Word(Opcode.DATA, -1, -1, -1),
		new Word(Opcode.DATA, -1, -1, -1)};
   }
}

