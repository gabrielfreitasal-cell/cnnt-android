# Plano de Testes — build atual

## Pré-requisitos

- APK debug gerado e instalado no dispositivo: `app/build/outputs/apk/debug/app-debug.apk`
- Dispositivo Android compatível com a build debug
- Para testes de stylus: caneta com botão lateral funcional
- Para testes de persistência: permitir fechar e reabrir o app no mesmo dispositivo
- Base inicial recomendada:
  - pelo menos uma página criada
  - canvas carregado
  - app aberto com a toolbar visível

## 1. Borracha pelo botão lateral da stylus

### Caso 1.1 — alternância temporária para borracha enquanto segura o botão

**Passos**

1. Abrir uma página com alguns traços visíveis no canvas.
2. Selecionar explicitamente a ferramenta de desenho.
3. Encostar a stylus no canvas sem apertar o botão lateral e confirmar que desenha normalmente.
4. Pressionar e segurar o botão lateral da stylus.
5. Sem soltar o botão, mover a stylus sobre traços existentes.
6. Soltar o botão lateral.
7. Tocar/desenhar novamente no canvas.

**Critérios de aceite**

- Enquanto o botão estiver pressionado, a ferramenta ativa muda temporariamente para borracha.
- A UI da toolbar reflete a borracha como ativa durante a pressão.
- Ao soltar o botão, a ferramenta anterior volta automaticamente.
- A UI da toolbar volta a refletir a ferramenta anterior.
- Não deve haver troca permanente indevida de ferramenta.

### Caso 1.2 — retorno correto para ferramenta anterior

**Passos**

1. Selecionar `Selecionar` na toolbar.
2. Pressionar e segurar o botão lateral da stylus.
3. Soltar o botão.

**Critérios de aceite**

- Durante a pressão, a toolbar indica `Borracha`.
- Após soltar, a toolbar volta para `Selecionar`.
- O comportamento é igual se a ferramenta anterior for `Laço` ou `Flashcard`.

## 2. Duplo-clique do botão lateral para laço

### Caso 2.1 — ativar laço por duplo-clique curto

**Passos**

1. Estar em qualquer ferramenta que não seja laço.
2. Fazer dois pressionamentos curtos no botão lateral da stylus, com intervalo aproximado menor que 300 ms.
3. Tocar no canvas e arrastar para formar uma área.

**Critérios de aceite**

- Após o duplo-clique curto, a ferramenta muda para `Laço`.
- A toolbar reflete `Laço` como ferramenta ativa.
- O arrasto desenha a área de seleção.

### Caso 2.2 — laço retangular mínimo

**Passos**

1. Ativar o laço via duplo-clique.
2. Arrastar em torno de um conjunto de traços.
3. Soltar o toque.

**Critérios de aceite**

- O laço retangular aparece visualmente durante o arrasto.
- O fluxo não deve causar crash.
- A seleção mínima atual deve ao menos delimitar área retangular e executar o fluxo existente de seleção.

## 3. Dashboard embutida de flashcards

### Caso 3.1 — abrir dashboard pelo ícone de flashcards

**Passos**

1. Abrir o app em uma página qualquer.
2. Tocar no ícone de flashcards da toolbar/menu.

**Critérios de aceite**

- Abre a tela/dialog `Flashcard Dashboard`.
- A dashboard mostra resumo com total de cards e vencidos.
- A dashboard lista cards existentes em `RecyclerView`.

### Caso 3.2 — criar card Basic pela dashboard

**Passos**

1. Abrir a dashboard.
2. Tocar em `+ Basic`.
3. Preencher `Frente`, `Verso` e opcionalmente tags.
4. Salvar.

**Critérios de aceite**

- O card aparece na lista.
- O card persiste no Room.
- Ao fechar e reabrir a dashboard, o item continua listado.

### Caso 3.3 — criar card Cloze pela dashboard

**Passos**

1. Abrir a dashboard.
2. Tocar em `+ Cloze`.
3. Informar texto usando sintaxe como `A capital é {{c1::Brasília}}`.
4. Salvar.

**Critérios de aceite**

- O card aparece na lista como `Cloze`.
- O preview do front oculta a oclusão.
- O card persiste no Room.

### Caso 3.4 — validação de cloze com limite de 2 oclusões

**Passos**

1. Abrir editor de cloze na dashboard.
2. Tentar salvar texto sem oclusão.
3. Tentar salvar texto com 3 oclusões.

**Critérios de aceite**

- Sem oclusão, o app mostra mensagem de validação e não salva.
- Com mais de 2 oclusões, o app mostra mensagem de validação e não salva.
- Não deve haver crash.

### Caso 3.5 — editar card existente

**Passos**

1. Abrir a dashboard.
2. Tocar em um item da lista ou em `Editar`.
3. Alterar conteúdo.
4. Salvar.

**Critérios de aceite**

- O item da lista é atualizado.
- O conteúdo novo persiste após fechar e reabrir a dashboard.

### Caso 3.6 — excluir card existente

**Passos**

1. Abrir a dashboard.
2. Tocar em `Excluir` em um card.

**Critérios de aceite**

- O card sai da lista imediatamente.
- O card é removido do Room.
- Ao reabrir a dashboard, o item não reaparece.

## 4. Rodada de revisão

### Caso 4.1 — iniciar revisão com todos os cards

**Passos**

1. Abrir a dashboard.
2. Deixar desmarcada a opção `Revisar só vencidos`.
3. Tocar em `Iniciar revisão`.
4. Virar card.
5. Responder usando um dos botões (`De novo`, `Difícil`, `Bom`, `Fácil`).

**Critérios de aceite**

- A rodada abre corretamente.
- O front é exibido primeiro.
- Após virar, aparece o verso.
- Ao responder, o card é atualizado no fluxo de revisão existente.

### Caso 4.2 — iniciar revisão só com vencidos

**Passos**

1. Garantir que exista ao menos um card vencido e um não vencido.
2. Abrir a dashboard.
3. Marcar `Revisar só vencidos`.
4. Iniciar revisão.

**Critérios de aceite**

- Apenas os cards vencidos entram na rodada.
- Se não houver vencidos, o app mostra feedback e não abre revisão vazia.

## 5. Flashcards no modo Canvas

### Caso 5.1 — ativar ferramenta Flashcard

**Passos**

1. Abrir o canvas.
2. Tocar no botão da ferramenta `Flashcard` na toolbar.

**Critérios de aceite**

- A toolbar destaca a ferramenta `Flashcard`.
- O canvas entra em modo de criação por arrasto.

### Caso 5.2 — criar bloco por arrasto

**Passos**

1. Com a ferramenta `Flashcard` ativa, tocar e arrastar no canvas.
2. Soltar para concluir a área.

**Critérios de aceite**

- Um bloco retangular é criado no canvas.
- O bloco renderiza preview textual.
- Ao concluir a criação, abre o editor do bloco.

### Caso 5.3 — criar bloco Basic no canvas

**Passos**

1. Criar um bloco por arrasto.
2. No editor do bloco, manter modo `Basic`.
3. Preencher frente e verso.
4. Salvar.

**Critérios de aceite**

- O card é salvo no Room.
- O card recebe origem `canvas-block`.
- O bloco mostra preview da frente no canvas.
- Tocar no bloco abre novamente o editor.

### Caso 5.4 — criar bloco Cloze no canvas

**Passos**

1. Criar um bloco por arrasto.
2. No editor do bloco, trocar para `Cloze`.
3. Informar texto com até 2 oclusões.
4. Salvar.

**Critérios de aceite**

- O card é salvo no Room.
- O bloco mostra preview com oclusão mascarada.
- O card entra no mesmo fluxo de revisão dos demais.

### Caso 5.5 — editar bloco existente por toque

**Passos**

1. Ter um bloco de flashcard já salvo no canvas.
2. Trocar para a ferramenta `Selecionar`.
3. Tocar no bloco sem arrastar.

**Critérios de aceite**

- O editor do bloco abre.
- O conteúdo existente é carregado corretamente.
- Alterações salvas atualizam preview e persistência.

### Caso 5.6 — excluir bloco do canvas

**Passos**

1. Abrir o editor de um bloco de flashcard do canvas.
2. Tocar em `Excluir bloco`.

**Critérios de aceite**

- O bloco sai do canvas.
- O card vinculado sai do Room.
- O item não reaparece após reabrir o app.

## 6. Persistência após reabrir o app

### Caso 6.1 — persistência de flashcards da dashboard

**Passos**

1. Criar pelo menos um `Basic` e um `Cloze` pela dashboard.
2. Fechar completamente o app.
3. Reabrir o app.
4. Abrir a dashboard novamente.

**Critérios de aceite**

- Os cards continuam listados.
- Tipo e conteúdo permanecem corretos.

### Caso 6.2 — persistência de blocos de flashcard no canvas

**Passos**

1. Criar um bloco de flashcard no canvas e salvar.
2. Fechar completamente o app.
3. Reabrir o app na mesma página.

**Critérios de aceite**

- O bloco continua no canvas.
- O preview continua visível.
- Ao tocar no bloco, o editor carrega o card correspondente.

## Bugs conhecidos / limitações desta build

- O laço ativado por duplo-clique usa o modo retangular existente como base mínima; não há ainda seleção semântica avançada de objetos.
- O feedback de duplo-clique depende do hardware/driver da stylus e do envio correto de `BUTTON_STYLUS_PRIMARY`.
- Cada bloco de flashcard no canvas suporta apenas 1 card nesta build.
- Não há mídia, reordenação complexa, pilhas avançadas ou séries múltiplas por bloco.
- Exclusão na dashboard foi entregue por botão; swipe não foi implementado nesta rodada.
- O preview do card no canvas é sucinto e focado em texto.