# Estado do projeto

Atualizado em 22 de agosto de 2026.

## Implementado

- Estrutura inicial do aplicativo Android.
- Interface inicial em português.
- Atalho para ativar o serviço de acessibilidade.
- Indicador de serviço ativado ou desativado.
- Modelo de perfil com quantidade variável de teclas, de 1 a 88.
- Geração de prévias para instrumentos pequenos e grandes.
- Conversão de notas para a tecla disponível mais próxima.
- Suporte interno a posições normalizadas, independente da resolução da tela.
- Serviço capaz de emitir um toque ou um acorde em coordenadas já calibradas.
- Calibração manual exibida sobre o jogo pelo serviço de acessibilidade.
- Botão flutuante para iniciar o mapeamento depois de abrir o jogo.
- Marcação numerada das teclas em ordem, com ações de desfazer, cancelar e salvar.
- Criação de perfis com nome e quantidade personalizada de teclas.
- Salvamento local e listagem de perfis calibrados.
- Importação de arquivos `.mid` e `.midi` pelo seletor de documentos do Android.
- Leitura local de MIDI nos formatos 0 e 1, com notas, canais, faixas, andamento e duração.
- Suporte a running status, note-on com velocidade zero e mudanças globais de andamento.
- Associação do arquivo importado a um perfil de instrumento calibrado.
- Salvamento local e exibição do resumo dos arquivos MIDI interpretados.
- Testes unitários da criação de layouts e do mapeamento de notas.
- Testes unitários da aplicação ordenada das posições calibradas.
- Testes unitários do parser MIDI e da conversão de pulsos em tempo.
- Compilação automática de APK pelo GitHub Actions.

## Ainda não implementado

- Edição e remoção de perfis salvos.
- Agendador de notas, velocidade e transposição na reprodução.
- Controle flutuante de iniciar, pausar e parar.
- Leitura de MIDI que usa divisão de tempo SMPTE; nesta versão são aceitos arquivos com pulsos por semínima.

## Próxima etapa

Agendar as notas interpretadas para reprodução, com ajuste de velocidade e transposição, usando o perfil calibrado associado.
