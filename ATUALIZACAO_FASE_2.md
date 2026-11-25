# Atualizar a versão que já funciona no seu computador

Esta versão mantém Java 17, Swing e MariaDB. Use o mesmo banco `falcon` e o mesmo `config.properties`. Não copie o diretório `target` antigo.

1. Feche todas as instâncias da versão antiga. Não use as duas versões ao mesmo tempo durante a atualização.
2. Faça uma cópia independente do banco antes de começar. No terminal, dentro da pasta do projeto: `sudo mariadb-dump --single-transaction falcon > ../falcon-antes-fase2.sql`. Guarde esse arquivo em local privado; ele contém documentos e contatos.
3. Entre como administrador com `sudo mariadb` e execute o conteúdo de `sql/atualizar-permissoes-fase2.sql`. Isso acrescenta a permissão ALTER ao usuário local do projeto. Não altera sua senha.
4. Extraia a nova implementação em outra pasta. Copie seu `config.properties` antigo para a nova raiz, ao lado de `pom.xml`. Sem o arquivo, a aplicação abre um banco H2 local. O argumento `--configurar` abre o assistente do MariaDB.
5. Importe a nova pasta como Maven no Eclipse. Se o projeto antigo ainda estiver aberto, feche/remova apenas a referência dele no workspace, sem apagar seus arquivos, antes de importar o projeto de mesmo nome. Mantenha JDK 17 no projeto e JavaSE-17 no ambiente de execução.
6. Execute `App.java`. Para uma base da fase 1, a aplicação gera um backup lógico adicional em `dados/backups` antes de acrescentar os campos e registra a versão 4 em `falcon_schema`. Se faltar permissão ou não puder salvar o backup, não prossiga com o uso; confira a mensagem. O arquivo de pendências não mostra o CPF completo.
7. Confira o número de clientes, veículos e locações e abra uma conta antiga. Na migração direta da fase 1, contratos já vencidos ficam como “Histórico anterior”, somente para consulta. Os demais começam confirmados. A categoria antiga é aproximada pelo cadastro atual do veículo. Documentos antigos não válidos e CPFs equivalentes em colisão ficam como Legado, mantendo IDs e vínculos.

## Recuperação

O MariaDB confirma comandos ALTER implicitamente: não existe rollback integral de DDL nesta migração. A migração é retomável e só marca a versão da migração após verificar IDs, vínculos e tarifas originais. Se houver falha, não volte a operar a versão antiga sobre um esquema parcialmente alterado. Corrija a permissão/conexão e execute a nova versão novamente, ou restaure o dump independente em um banco separado para investigar. Não importe um backup por cima do banco ocupado.

Os backups automáticos SQL têm INSERTs e exigem um esquema compatível vazio para restauração; o dump independente do passo 2 inclui o esquema original. Nenhum backup, credencial local ou dado de atendimento vai no ZIP ou no Git.

## O que muda no uso

- Clientes novos: CPF válido com ou sem máscara, com validação e erro junto ao campo. Legados podem continuar alugando e editar nome/contato; trocar o documento exige CPF válido e único.
- Locações: Confirmada → Em uso → Concluída, ou Confirmada → Cancelada com motivo. Retirada é registrada hoje. Devolução admite data efetiva entre a retirada e hoje e guarda separadamente o instante do registro.
- Disponibilidade: datas inclusivas, início de nova reserva a partir de hoje, canceladas não bloqueiam; devolução antecipada libera no dia seguinte; atraso em uso bloqueia o futuro até devolver.
- A reserva posterior permanece no histórico se a anterior atrasar. Sua retirada é recusada durante o uso anterior e no próprio dia da devolução; pode ocorrer no dia seguinte se ainda estiver no período previsto.
- Conta: preserva datas e tarifas contratadas. Na devolução, uma multa por dia corrido de atraso é calculada pela diária do veículo que consta no contrato. Estornos e recebimentos são tratados fora do sistema.
- Demanda mensal: cinco categorias, canceladas excluídas, mês da retirada prevista e categoria aplicada na confirmação. Não representa receita nem ocupação.

## Bases da versão 2.0 ou 2.1

A atualização para o esquema 3 acrescenta os campos de origem, classificação histórica e instante de registro da devolução. Preserva estados e tarifas existentes. A versão 2.0 não registrava a proveniência individual dos contratos nem o instante do lançamento das devoluções. O Falcon mantém os dados conhecidos e não reclassifica esses contratos por suposição. A identificação de categoria aproximada é possível na importação direta de uma base da fase 1.

O esquema 4 acrescenta o campo da multa. A atualização preserva os valores dos contratos já encerrados. Nas próximas devoluções, a multa é gravada junto da data efetiva.
