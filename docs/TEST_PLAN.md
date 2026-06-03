# TEST PLAN

## Escopo da versão

- Novo domínio persistente de blocos e links.
- Painel lateral de conteúdo com drag-and-drop.
- Overlay de blocos com renderização por tipo.
- Ligações visuais entre blocos e painel de backlinks.
- Mini-map, centralizar, fit-all e busca global.
- Export atualizado com blocos, links e backup expandido.
- Integração com handwriting/stylus sem regressão funcional.

## 1. Modelo de domínio + persistência

### Passos
1. Instalar o app sobre uma base já usada anteriormente.
2. Abrir o app e validar que notas/strokes antigas continuam presentes.
3. Verificar se flashcards antigos continuam acessíveis.
4. Criar novos blocos de texto, markdown e flashcard.
5. Fechar totalmente o app.
6. Reabrir o app.
7. Confirmar que posição, tamanho e conteúdo dos blocos persistiram.
8. Confirmar que links criados também persistem após reabrir.

### Aceite
- A migration não destrói dados anteriores.
- Flashcards antigos continuam existentes.
- Novos blocos persistem no Room.
- Links persistem após reabrir.

### Limitações conhecidas
- A migração de assets antigos depende do que já existia salvo localmente.

## 2. Sidebar drag-source / painel de conteúdo

### Passos
1. Abrir a sidebar.
2. Validar categorias e itens do painel de conteúdo.
3. Fazer long-press em `Texto` e arrastar para o canvas.
4. Repetir para `Markdown`, `Flashcard`, `Questão interativa`, `Imagem` e `PDF`.
5. Validar haptic no início do drag.
6. Validar animação de entrada do bloco ao soltar.
7. Validar tooltip/descrição ao manter pressionado.
8. Testar scroll do painel com todos os itens.

### Aceite
- O drag usa o Android Drag and Drop.
- O bloco nasce na coordenada correta do mundo.
- O visual do painel está agrupado, legível e consistente.

### Limitações conhecidas
- O drag source mostra ghost customizado simples, não miniatura real do conteúdo.

## 3. Renderização de blocos — overlay de views

### Texto
1. Criar um bloco `Texto`.
2. Escrever conteúdo.
3. Tirar foco do campo.
4. Fechar e reabrir o app.

Aceite:
- Auto-save no blur/debounce.
- Conteúdo preservado.

### Markdown
1. Criar bloco `Markdown`.
2. Inserir título, lista e checkbox markdown.
3. Dar double-tap ou usar toggle de edição/preview.
4. Validar renderização rica.

Aceite:
- Preview e edição alternam corretamente.
- Reflow acompanha resize.

### Flashcard
1. Criar bloco `Flashcard`.
2. Abrir fluxo de edição existente.
3. Editar frente/verso.
4. Validar preview no bloco.

Aceite:
- Integra renderer atual ao novo bloco.

### Questão interativa
1. Criar `Questão interativa`.
2. Dar double-tap e editar pergunta/opções.
3. Responder.
4. Validar feedback visual de acerto/erro.
5. Fechar e reabrir o app.

Aceite:
- Estado da resposta persiste no `contentJson`.
- Erro e acerto têm feedback visual distinto.

### Imagem
1. Criar bloco `Imagem`.
2. Dar double-tap.
3. Escolher imagem via SAF.
4. Validar carregamento, placeholder e resize.

Aceite:
- Imagem abre no picker do sistema.
- Render usa fit/crop consistente.

### PDF
1. Criar bloco `PDF`.
2. Dar double-tap.
3. Escolher PDF válido via SAF.
4. Navegar página anterior/próxima.

Aceite:
- PDF carrega com paginação.
- Indicador de página atualiza.

### Limitações conhecidas
- Fullscreen reader de PDF está parcial e sinalizado como próximo passo.

## 4. Interações com blocos

### Passos
1. Tocar em um bloco para selecionar.
2. Arrastar o corpo do bloco.
3. Soltar e confirmar persistência.
4. Arrastar handles laterais e de canto para resize.
5. Validar tamanho mínimo.
6. Fazer long-press e usar menu contextual:
   - Editar
   - Duplicar
   - Bring to front
   - Send to back
   - Excluir
7. Repetir com mais de um bloco.

### Aceite
- Seleção destaca borda/handles.
- Move e resize persistem.
- Duplicação cria novo bloco deslocado.
- Ordenação z-index muda visualmente.

### Limitações conhecidas
- Multi-seleção de blocos está funcional por seleção de fluxo geral, mas ainda não cobre todos os refinamentos de caixa de seleção dedicada por overlay.

## 5. Sistema de ligações

### Passos
1. Criar pelo menos 3 blocos.
2. Selecionar um bloco.
3. Arrastar do handle `→` até outro bloco.
4. Validar glow em blocos válidos.
5. Soltar em um bloco destino.
6. Tocar na ligação criada.
7. Editar rótulo.
8. Excluir a ligação.
9. Validar badges `← N` e `→ N`.
10. Abrir backlinks panel ao selecionar bloco com relações.
11. Tocar em badge e navegar para bloco relacionado.

### Aceite
- Curva Bezier renderiza com feedback claro.
- Hit-test do link funciona ao tocar próximo à curva.
- Badges mostram contagem de entrada e saída.
- Painel de backlinks lista relacionados e centraliza neles.

### Limitações conhecidas
- Handle dedicado é o fluxo principal; toggle de modo link existe como apoio visual/comportamental.

## 6. Navegação e affordances

### Passos
1. Ativar mini-map.
2. Criar blocos espalhados.
3. Navegar tocando no mini-map.
4. Usar `Centralizar`.
5. Usar `Fit-all`.
6. Abrir busca global.
7. Buscar texto presente em:
   - bloco texto
   - markdown
   - questão interativa
8. Tocar no resultado.

### Aceite
- Mini-map mostra densidade/posicionamento geral dos blocos.
- Busca centraliza no resultado selecionado.
- Centralizar e fit-all funcionam sem quebrar o canvas.

### Limitações conhecidas
- Mini-map está focado nos blocos do novo overlay, não em strokes puros.

## 7. Export atualizado

### PNG/PDF
1. Criar canvas com strokes, blocos e links.
2. Exportar PNG.
3. Exportar PDF.
4. Abrir os arquivos gerados.

Aceite:
- PNG/PDF incluem strokes, blocos e linhas de link.

### ZIP backup
1. Abrir export.
2. Escolher backup ZIP.
3. Salvar via SAF.
4. Inspecionar o conteúdo do ZIP.

Validar presença de:
- `manifest.json`
- `room/notebooks.json`
- `room/boards.json`
- `room/layers.json`
- `room/strokes.json`
- `room/spatial_objects.json`
- `room/flashcards.json`
- `room/blocks.json`
- `room/links.json`
- `assets/images/` quando houver
- `assets/pdfs/` quando houver

### Aceite
- Backup contém os novos JSONs de blocos e links.
- Export visual não regrediu.

### Limitações conhecidas
- Inclusão de asset binário local no ZIP depende de URI local acessível.

## 8. Integração com handwriting/stylus

### Passos
1. Com ferramenta desenho, usar stylus sobre área sem bloco.
2. Confirmar desenho normal.
3. Com stylus, tocar sobre bloco não selecionado.
4. Confirmar que o desenho continua possível sem perder stroke.
5. Selecionar bloco e interagir com ele por toque.
6. Usar botão da stylus para borracha perto de um bloco.
7. Confirmar que apenas strokes são apagados.
8. Usar dedo para pan/zoom.

### Aceite
- Stylus continua desenhando por padrão.
- Dedo continua em pan/zoom.
- Borracha não remove blocos.
- Bloco só intercepta adequadamente quando o fluxo é de bloco selecionado/interação explícita.

### Limitações conhecidas
- Ajustes finos de prioridade de eventos stylus x overlay ainda devem ser validados em hardware real.

## 9. Undo/redo e robustez

### Passos
1. Criar strokes e usar undo/redo.
2. Mover objeto antigo e usar undo/redo.
3. Criar bloco novo.
4. Editar texto do bloco.
5. Fechar e reabrir rapidamente após edição.

### Aceite
- Undo/redo antigo de strokes continua funcional.
- Autosave dos novos blocos evita perda de edição.

### Limitações conhecidas
- Undo/redo global para blocos/links está parcial nesta versão e precisa expansão para stack unificada completa.

## 10. Regressão geral recomendada

1. OCR de tela visível.
2. OCR por região.
3. Dashboard de flashcards com swipe-to-delete.
4. Laço no canvas para seleção antiga de objetos espaciais.
5. Export JSON/PNG/PDF/ZIP.
6. Abrir e fechar o app múltiplas vezes.
7. Validar desempenho com muitos blocos na mesma página.

## Bugs/limitações conhecidos para validação em tablet

- Fullscreen de PDF está parcial.
- Undo/redo de blocos/links ainda não cobre todos os fluxos.
- Assets SAF podem exigir validação extra em dispositivos com fornecedores de arquivos diferentes.
- Multi-seleção de blocos ainda merece refinamento fino de UX em comparação ao laço já existente do canvas clássico.