# CHANGELOG v0.3

## Resumo executivo

Esta versão consolida a base do CNNT como ferramenta visual de conhecimento com blocos persistidos, ligações entre ideias e uma experiência de canvas mais próxima de produto.

## Entregas principais

### 1. Novo domínio persistente
- Novas entidades Room: `ContentBlock` e `LinkEdge`.
- Migração de banco sem destruição de dados.
- Migração dos flashcards antigos para blocos persistentes do novo domínio.

### 2. Painel lateral de conteúdo
- Sidebar evoluída para painel de conteúdo com categorias.
- Drag-and-drop de tipos de bloco direto para o canvas.
- Tooltips, espaçamento maior e visual mais consistente.

### 3. Blocos com overlay de Views
- Overlay dedicado acima do canvas para blocos interativos.
- Tipos implementados: texto, markdown, flashcard, questão interativa, imagem e PDF.
- Seleção visual, handles de resize, animações de criação/remoção e menu contextual.

### 4. Interações avançadas
- Seleção, movimento, resize e duplicação de blocos.
- Multi-seleção de blocos integrada ao fluxo geral da tela.
- Busca global de blocos com centralização no resultado.

### 5. Ligações entre blocos
- Overlay de links com curvas Bezier e hit-test.
- Criação por handle dedicado.
- Edição/exclusão de rótulo.
- Painel de backlinks com navegação entre relacionados.

### 6. Navegação e visão geral
- Mini-map opcional.
- Ações de centralizar e fit-all.
- Badges de entrada/saída por bloco.

### 7. Export atualizado
- JSON enriquecido com blocos e links.
- PNG/PDF com blocos e links desenhados.
- ZIP com `blocks.json`, `links.json` e assets binários locais quando disponíveis.

## Bibliotecas adicionadas
- **Markwon**: renderização Markdown robusta com tabelas/task list/html.
- **Glide**: carregamento eficiente de imagens e cache.
- **AndroidPdfViewer**: visualização paginada de PDFs.

## Trade-offs desta rodada
- O PDF fullscreen ficou sinalizado visualmente como próximo passo.
- O export de assets binários depende de URI `file://` local para inclusão direta no ZIP.
- O undo/redo global para blocos/links foi parcialmente preparado pela nova base, mas ainda precisa ser expandido para cobrir todos os casos complexos com histórico unificado.