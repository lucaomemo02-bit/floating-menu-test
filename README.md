# Menu Flutuante Teste

App Android (Kotlin) com um menu flutuante sobre outros apps, para testar layout, arraste e comportamento de toque de uma sobreposição.

## Estrutura

| Arquivo | Função |
|---|---|
| `MainActivity.kt` | Pede a permissão de sobreposição e liga/desliga o serviço |
| `FloatingMenuService.kt` | Serviço em primeiro plano: ícone, painel e janelas no `WindowManager` |
| `DraggableTouchListener.kt` | Arraste reutilizável (diferencia clique de arraste, limita à tela) |
| `res/layout/floating_icon.xml` | Ícone recolhido |
| `res/layout/floating_panel.xml` | Painel expandido com os controles de teste |

## Controles do painel

- Caixa de seleção: contorno de layout
- Interruptor: modo compacto
- Radio: largura do painel
- Spinner: cor do cabeçalho
- Controle deslizante: opacidade
- Botões: resetar posição, limpar log
- Log de eventos: mostra qual controle recebeu o toque

## Como usar

1. Instale o APK e abra o app.
2. Toque em **Conceder permissão de sobreposição** e ative para este app.
3. Toque em **Iniciar menu flutuante**.
4. Toque no ícone para expandir, arraste pelo cabeçalho, use **—** para recolher.
5. Para parar: botão no app ou ação **Parar** na notificação.

## Compilar

- GitHub Actions: o workflow `.github/workflows/build.yml` gera o APK de debug a cada push (aba *Actions* → artefato `menu-flutuante-debug`).
- Local: `gradle assembleDebug` (Gradle 8.7+ e JDK 17).

Requisitos: Android 7.0+ (API 24), compilado para API 34.
