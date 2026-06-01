# CNNT — Checklist de verificação (v0.2.2-beta)

## Modo diagnóstico (beta)

Toque **5 vezes** no título "CanNote Workspace" no topo. Overlay mostra zoom, traços, cache, RAM e hora do último save.

Use após instalar o APK. Marque: ✅ passou · ⚠️ parcial · ❌ falhou

## Persistência (crítico — esta versão)

| # | Teste | Esperado |
|---|--------|----------|
| P1 | Escrever vários traços, fechar app (remover dos recentes), reabrir | Traços visíveis na mesma página |
| P2 | Criar **+ Página**, desenhar na página 2, reabrir app | Página 2 ativa ou lista correta |
| P3 | Apagar traços com borracha, reabrir | Traços apagados não voltam |

## Performance

| # | Teste | Esperado |
|---|--------|----------|
| F1 | Escrever 3 min sem parar | Sem lag crescente forte |
| F2 | Pinch zoom e soltar dedos | Sem “pulo” do canvas |

## Ferramentas

| # | Teste | Esperado |
|---|--------|----------|
| T1 | Trocar pincéis e tamanho (mm) | Traço coerente |
| T2 | Sidebar → trocar página | Canvas atualiza |
| T3 | Laço + seleção | Seleciona traços |

## Ainda não coberto nesta versão

- OCR real (diálogo placeholder)
- IMAGE/PDF com file picker
- Backup ZIP completo
- Navegação Home/Recentes (toast “Em breve”)
