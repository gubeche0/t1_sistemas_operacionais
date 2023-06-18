
Brenda Rodrigues da Silva
Gustavo Beche Lopes
Jeniffer Moreira Borges

Seção Implementação: 
    Foi implementada todas as característica solicitadas ao trabalho.

    Não possui nenhuma restrição, apenas um comportamento estranho foi observado ao executar os programas de testes 
    onde alguns deles acabavam causando a interrupcao `intEnderecoInvalido` ao tentar acessar um endereço de memoria
    invalido. Como por exemplo no programa `progMinimo` onde tenta acessar o endereço de memoria 10 na instrução `STD`
    porem esse programa só possui apenas 7 instrucoes (e o tamanho das paginas/frame é 8) logo acaba causando essa interrupcao.
    Como no enunciado estava escrito que os programas não deveria ser alterado não foi corrigido esse problema idendificado nos
    exemplos de códigos.

Seção Testes
    Ao executar o programa o sistema já carrega por padrão 2 processos, sendo o primeiro o `fibonacci10` e o segundo `fatorial`.

    Os seguintes comandos estão disponiveis para o uso:

    `help` - Mostra todos os comandos disponiveis
    `ps` - Mostra todos os processos em execução
    `kill` - Mata o processo com o pid solicitado
    `exec` - Executa o programa solicitado
    `load` - Carrega o programa solicitado
    `dump memory` - Mostra o conteudo da memoria entre o endereço inicial e final solicitado
    `dump process` - Mostra o conteudo do processo solicitado
    `trace on` - Ativa o modo de trace
    `trace off` - Desativa o modo de trace
    `exit` - Sai do sistema
