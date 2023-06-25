
Brenda Rodrigues da Silva
Gustavo Beche Lopes
Jeniffer Moreira Borges

Seção Implementação: 
    Foi implementada todas as característica solicitadas ao trabalho.

    A cada instrução executada na cpu é chamado o método clock.tick() para simular o tempo de execução da instrução. A cada 5 instruções executadas é criado uma interrupção intTimerSlice que ao ser tratada remove o processo atual da CPU e carrega o proximo processo que estiver na fila de pronto.

    Ao executar uma chamada do sistema caso seja do tipo input ou output o processo também é removido da CPU e colocado no estado de bloqueado.
    A requisição de IO é colocado numa fila para ser processado.
    Uma thread é responsavel por ficar em loop verificando se existe alguma requisição de IO na fila, caso exista ele solicitado acesso ao recurso do terminal que é controlado com um semáforo e compartilhado com o shell. Ao obter o recurso ele efetua a operação de IO e então efetua uma interupção a CPU para avisar que a operação está pronta.

    Ao receber a interrupção intIOFinish o processo é colocado novamente na fila de pronto sem que haja interupcao no processo atual.

    Como a thread que controla a execução das operação IO e o shell compartilham o mesmo recurso, foi utilizado um semáforo para controlar o acesso ao terminal.
    Por esse motivo as operação de IO podem ficar bloqueadas enquanto o shell estiver esperando uma entrada do usuário, sendo nescessario que o usuário pressione enter para liberar o recurso e assim a operação de IO possa ser executada.

    Foi nescessario fazer uma pequena alteração no funcionamento da CPU para quando não houver nenhum processo que pode ser executado na CPU, fosse pulado as etapas de fetch e decode e execute, só sendo executado a verificaçao de interrupção e o tratamento da mesma.
    Não foi implementado um semaforo como sugerido na especificação, pois a verificação das interrupções deveriam permanecer sendo executado mesmo quando não houver nenhum processo na CPU para que os processoss bloqueados pudesse ser desbloqueados.
    Uma alternativa seria ter criado um processo um loop e ficasse executado para sempre, no entanto tao solução causaria uma poluição visual na visualização do trace, alem de ocuparia a CPU desnecessariamente.
    Considerando que no mundo real a CPU nunca ficaria sem processo para executar considerando que o proprio sistema operacional é um processo, foi decidido que a CPU não ficaria em loop quando não houvesse nenhum processo para executar.



Seção Testes
    Ao executar o programa o sistema já carrega por padrão 4 processos, sendo o primeiro o `fibonacci10`, o segundo `fatorial` o terceiro um 'testeOutput' e 'testeInput'.

    Para começar a execução da CPU basta utilizar o comando "execAll" no shell.

    Feito isso os processos começarão a ser executados. Quando os processos 'testeOutput' e 'testeInput' fizerem as chamadas do sistema eles ficarão em estado bloqueado até o console imprimir na tela o output e solicitar o imput.

    Para que o console imprima o output e solicite o input precisará ganhar acesso ao shell para isso basta executar qualquer comando para que o shell fique livre e o console possa executar as operações de IO.

    Os seguintes comandos estão disponiveis para o uso:

    `help` - Mostra todos os comandos disponiveis
    `ps` - Mostra todos os processos em execução
    `kill` - Mata o processo com o pid solicitado
    `execAll` - Inicia a execução da CPU, executando todos os processos
    `load` - Carrega o programa solicitado
    `dump memory` - Mostra o conteudo da memoria entre o endereço inicial e final solicitado
    `dump process` - Mostra o conteudo do processo solicitado
    `trace on` - Ativa o modo de trace
    `trace off` - Desativa o modo de trace
    `exit` - Sai do sistema
