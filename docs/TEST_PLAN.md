# TEST PLAN

## Escopo desta rodada

- Swipe-to-delete na dashboard de flashcards com Snackbar de desfazer.
- Laço real no canvas para selecionar objetos por polígono/retângulo, mover grupo e excluir seleção.
- OCR com duas opções: por região e pela tela visível.
- Export de backup completo `.zip` via Storage Access Framework.

## 1. Flashcards — swipe-to-delete

### Passos
1. Abrir a tela de flashcards pelo botão da toolbar.
2. Garantir que existam pelo menos 2 cards.
3. Na lista da dashboard, arrastar um item para a esquerda.
4. Verificar se o item some da lista imediatamente.
5. Verificar se a contagem do topo atualiza.
6. Tocar em `Desfazer` dentro de ~5 segundos.
7. Repetir o swipe e aguardar o Snackbar expirar sem desfazer.
8. Fechar e reabrir a dashboard.

### Aceite
- Swipe horizontal à esquerda funciona na lista da dashboard.
- O card só é removido definitivamente após o Snackbar expirar.
- `Desfazer` restaura o card e as contagens.
- Após expirar, a remoção persiste ao reabrir a tela.

### Limitações conhecidas
- O swipe foi implementado na dashboard existente de flashcards, não em um diálogo separado dedicado.

## 2. Laço real no canvas

### Passos
1. Inserir 2 ou mais blocos no canvas.
2. Ativar o laço pela toolbar.
3. Fazer seleção livre envolvendo os centros dos blocos.
4. Verificar highlight agrupado da seleção.
5. Arrastar a seleção e confirmar que os objetos se movem em conjunto.
6. Usar a ação de excluir seleção na toolbar.
7. Inserir novos blocos e repetir com laço retangular pelo menu overflow.
8. Testar duplo clique curto no botão da stylus para entrar em laço.

### Aceite
- O laço livre seleciona objetos cujo centróide cai dentro do polígono.
- O laço retangular segue funcionando como fallback.
- A seleção múltipla exibe overlay visível.
- É possível mover a seleção agrupada.
- É possível excluir a seleção pela toolbar/menu.

### Limitações conhecidas
- A seleção usa centróide do objeto, não interseção geométrica completa.
- A exclusão da seleção é focada em objetos espaciais, não strokes.

## 3. OCR por região

### Passos
1. Abrir o diálogo de OCR.
2. Tocar em `OCR por região`.
3. Desenhar um retângulo/região no canvas.
4. Tocar novamente em `OCR por região` se necessário para consumir a região marcada.
5. Verificar o texto reconhecido no diálogo.
6. Testar `Copiar`.
7. Testar `Criar Flashcard`.
8. Testar `OCR da tela visível` e comparar com a opção regional.

### Aceite
- Existem duas opções separadas: região e tela visível.
- O OCR por região usa só a área selecionada.
- O resultado pode ser copiado.
- O resultado pode virar flashcard.

### Limitações conhecidas
- O recorte usa coordenadas da bitmap capturada da view.
- Dependendo do conteúdo/zoom, a precisão do OCR pode variar.

## 4. Backup completo ZIP

### Passos
1. Abrir `Exportar`.
2. Tocar em `Backup CNNT (ZIP completo)`.
3. Escolher destino pelo seletor do sistema.
4. Confirmar o salvamento.
5. Validar que o arquivo `.zip` foi criado.
6. Abrir o ZIP em um gerenciador de arquivos/PC.
7. Confirmar presença de:
   - `manifest.json`
   - `room/notebooks.json`
   - `room/boards.json`
   - `room/layers.json`
   - `room/strokes.json`
   - `room/spatial_objects.json`
   - `room/flashcards.json`
8. Se houver PNGs em cache, confirmar pasta `cache_png/`.

### Aceite
- O destino é escolhido via SAF.
- O ZIP contém dump Room em JSON.
- O ZIP contém manifest com versão do app.
- O ZIP inclui PNGs de cache quando existirem.

### Limitações conhecidas
- Import não foi implementado nesta rodada.
- Se não houver PNGs em cache, a pasta `cache_png/` pode vir vazia ou ausente.

## 5. Regressão rápida recomendada

1. Criar/editar/excluir flashcards manualmente.
2. Criar bloco flashcard no canvas e editar.
3. Desenhar com caneta, borracha e mover blocos simples.
4. Exportar JSON, PNG e PDF além do ZIP.
5. Confirmar que o app abre e fecha sem crash.

## Bugs/observações para validar depois

- Confirmar se o gesto de duplo clique da stylus está confortável em hardware real.
- Confirmar se o OCR por região precisa de um toque extra no fluxo atual para consumir a seleção.
- Confirmar se todos os itens do Room aparecem corretamente no ZIP em base com dados reais.