#!/usr/bin/env python3
"""
Gera a arte animada do PLAYER DE ÁUDIO do TuneGrab para o README
(docs/player-audio-demo.gif).

Um mockup da tela do player de MÚSICA, com a mesma paleta e o mesmo
comportamento do app:

  - as "barrinhas de DJ" (VisualizerView) dançam com o espectro real;
  - o motor de batida do app (grave acima da média = soco) é SIMULADO com
    as MESMAS constantes do VisualizerView, a 60 fps — o GIF só amostra
    alguns quadros, mas os números são os do app;
  - quando a música pausa, as barras CAEM SOZINHAS (o alvo vai a zero e o
    decay faz o resto) — exatamente o que a view real faz;
  - a barra de progresso anda, os tempos correm junto, o play vira pause.

O GIF é gerado frame a frame com Pillow — nada de imagem do app, tudo
desenhado com a paleta oficial (app/src/main/res/values/colors.xml).

Uso:  python3 tools/gerar_arte_audio.py
"""

import math
import os
from PIL import Image, ImageDraw, ImageFilter, ImageFont

# ---------- paleta (copiada de app/src/main/res/values/colors.xml) ----------
BG = (0x14, 0x12, 0x0D)
SURFACE = (0x21, 0x1E, 0x15)
STROKE = (0x3A, 0x33, 0x20)
PRIMARY = (0xE8, 0xC1, 0x58)
SECONDARY = (0xC9, 0x97, 0x4C)
ON_BG = (0xF1, 0xEE, 0xE5)
ON_SURFACE_VAR = (0xA7, 0x9F, 0x8C)
ICON_BG_START = (0xA0, 0x6E, 0x12)
ICON_BG_END = (0xF5, 0xD1, 0x61)
STATE_DONE = (0x4A, 0xDE, 0x80)

# cores do VisualizerView (app/src/main/java/com/tunegrab/app/VisualizerView.kt)
BAR_TOP = (0xF5, 0xD1, 0x61)
BAR_MID = (0xE8, 0xC1, 0x58)
BAR_BOTTOM = (0xA0, 0x6E, 0x12)
IDLE_BAR = (0x30, 0xE8, 0xC1, 0x58)     # grade apagada
PEAK = (0xE6, 0xE7, 0xE9, 0xF0)         # pontinho de pico

# ---------- geometria ----------
W, H = 420, 700
# a tela real tem ~360dp de largura; a arte tem 420px -> escala 1.167
DP = 1.167

FPS = 12
DURACAO = 6.0
N_FRAMES = int(FPS * DURACAO)

# o VisualizerView roda a ~60fps; simulamos nessa taxa e amostramos no GIF
SIM_FPS = 60

FONTE_DIR = "/usr/share/fonts/noto"
F_TITULO = os.path.join(FONTE_DIR, "NotoSans-Bold.ttf")
F_TEXTO = os.path.join(FONTE_DIR, "NotoSans-Medium.ttf")


def fonte(caminho, tamanho):
    try:
        return ImageFont.truetype(caminho, tamanho)
    except Exception:
        return ImageFont.load_default()


def dp(v):
    return int(round(v * DP))


def sp(v):
    """sp -> px (um pouco maior que o dp: legibilidade no README)."""
    return int(round(v * DP * 1.18))


# ---------- motor do VisualizerView (constantes iguais às do app) ----------
BARS = 28
COLUMNS = BARS * 2
GAIN = 2.2
CURVE = 0.7
TILT_HI = 0.25
DECAY = 0.02
GRAVITY = 0.002
MAX_FALL = 0.016
BASS_BANDS = 6
BASS_SMOOTH = 0.05
BASS_FLOOR = 0.06
BEAT_RATIO = 0.28
BEAT_MIN_SPIKE = 0.015
BEAT_GAP_MS = 110
PUNCH_GAIN = 2.4
MAX_PUNCH = 1.15
PUNCH_DECAY = 0.90
BEAT_BANDS = 12


class Visualizador:
    """O motor do VisualizerView, com a MESMA matemática do app."""

    def __init__(self):
        self.display = [0.0] * BARS
        self.peaks = [0.0] * BARS
        self.peak_vel = [0.0] * BARS
        self.bass_avg = 0.0
        self.punch = 0.0
        self.last_beat_ms = -9999

    def espectro(self, t_ms):
        """28 bandas ficticias que parecem uma musica (kick + corpo + hat)."""
        # batida a 120 BPM (500ms), colcheia a 250ms
        beat_f = (t_ms % 500) / 500.0
        kick = math.exp(-beat_f * 7.0)
        hat_f = (t_ms % 250) / 250.0
        hat = math.exp(-hat_f * 16.0)
        corpo = 0.5 + 0.5 * math.sin(t_ms / 260.0)
        brilho = 0.55 + 0.45 * math.sin(t_ms / 90.0 + 1.1)
        # o "groove": um vaivem lento que faz a musculatura wholepectro variar
        groove = 0.72 + 0.28 * math.sin(t_ms / 700.0)

        bandas = []
        for i in range(BARS):
            grave = max(0.0, 1.0 - i / 9.0)          # o grave domina
            medio = math.exp(-((i - 13) / 9.0) ** 2)   # corpo no meio
            agudo = max(0.0, (i - 17) / 10.0)          # agudo so no fim
            v = (
                0.95 * grave * kick
                + 0.42 * medio * (0.45 + 0.55 * corpo) * groove
                + 0.38 * agudo * hat * brilho
            )
            bandas.append(max(0.0, min(1.0, v)))
        return bandas

    def passo(self, t_ms, tocando):
        """Um passo de 1/60s — o ingest() + o draw() do app."""
        if tocando:
            bands = self.espectro(t_ms)
        else:
            bands = [0.0] * BARS          # sem dado novo -> alvo zero

        # ---- motor de batida (igual ao app) ----
        soma = sum(bands[:BASS_BANDS])
        bass = soma / BASS_BANDS
        self.bass_avg += (bass - self.bass_avg) * BASS_SMOOTH
        spike = bass - self.bass_avg
        if (bass > BASS_FLOOR
                and spike > self.bass_avg * BEAT_RATIO + BEAT_MIN_SPIKE
                and t_ms - self.last_beat_ms > BEAT_GAP_MS):
            self.punch = min(MAX_PUNCH, self.punch + spike * PUNCH_GAIN)
            self.last_beat_ms = t_ms
        self.punch *= PUNCH_DECAY
        if self.punch < 0.01:
            self.punch = 0.0

        # ---- alvo e desenho ----
        for i in range(BARS):
            raw = bands[i]
            beat_w = (1.0 - i / (BEAT_BANDS + 4.0)) if i < BEAT_BANDS else 0.0
            g = GAIN * (1.0 + TILT_HI * i / (BARS - 1)) * (1.0 + self.punch * beat_w)
            target = min(1.0, max(0.0, (raw * g) ** CURVE))
            d = self.display[i]
            self.display[i] = target if target > d else max(0.0, d - DECAY)
            if target >= self.peaks[i]:
                self.peaks[i] = target
                self.peak_vel[i] = 0.0
            else:
                self.peak_vel[i] = min(MAX_FALL, self.peak_vel[i] + GRAVITY)
                self.peaks[i] = max(0.0, self.peaks[i] - self.peak_vel[i])


# ---------- desenho ----------


def gradiente_vertical(d, x0, y0, x1, y1, topo, meio, base):
    """Preenche um retangulo com o gradiente do visualizador."""
    altura = y1 - y0
    for y in range(y0, y1):
        t = (y - y0) / max(1, altura - 1)
        if t < 0.55:
            u = t / 0.55
            cor = tuple(int(base[i] + (meio[i] - base[i]) * u) for i in range(3))
        else:
            u = (t - 0.55) / 0.45
            cor = tuple(int(meio[i] + (topo[i] - meio[i]) * u) for i in range(3))
        d.line((x0, y, x1, y), fill=cor)


def nota_musical(d, cx, cy, tam, cor):
    """Duas notasMusicais (como o ic_music_note do app, simplificado)."""
    h = tam * 1.05
    raio = tam * 0.25
    # hastes verticais
    d.rounded_rectangle((cx - tam * 0.26, cy - h / 2, cx - tam * 0.16, cy + h * 0.06),
                        radius=int(tam * 0.05), fill=cor)
    d.rounded_rectangle((cx + tam * 0.22, cy - h * 0.58, cx + tam * 0.32, cy - h * 0.02),
                        radius=int(tam * 0.05), fill=cor)
    # a ligacao entre as duas hastes (a "bandeirinha"), em diagonal
    d.polygon([(cx - tam * 0.26, cy - h / 2),
               (cx + tam * 0.32, cy - h * 0.58),
               (cx + tam * 0.32, cy - h * 0.34),
               (cx - tam * 0.26, cy - h * 0.26)], fill=cor)
    # cabecas
    d.ellipse((cx - tam * 0.26 - raio, cy + h * 0.06 - raio * 0.85,
               cx - tam * 0.26 + raio, cy + h * 0.06 + raio * 0.85), fill=cor)
    d.ellipse((cx + tam * 0.22 - raio, cy - h * 0.02 - raio * 0.85,
               cx + tam * 0.22 + raio, cy - h * 0.02 + raio * 0.85), fill=cor)


def microfone(d, cx, cy, tam, cor):
    """Microfone (o ic_lyrics do app)."""
    w = tam * 0.46
    d.rounded_rectangle((cx - w / 2, cy - tam * 0.44, cx + w / 2, cy + tam * 0.06),
                        radius=int(w / 2), fill=cor)
    # o arco do suporte (em U, abra pra cima)
    espessura = max(2, int(tam * 0.10))
    d.arc((cx - tam * 0.30, cy - tam * 0.22, cx + tam * 0.30, cy + tam * 0.38),
          start=0, end=180, fill=cor, width=espessura)
    # haste e base
    d.line((cx, cy + tam * 0.26, cx, cy + tam * 0.40), fill=cor, width=espessura)
    d.line((cx - tam * 0.16, cy + tam * 0.42, cx + tam * 0.16, cy + tam * 0.42),
           fill=cor, width=espessura)


def icone_play(d, cx, cy, tam, cor):
    h = tam * 0.66
    w = h * 0.88
    x0 = cx - w * 0.36
    d.polygon([(x0, cy - h / 2), (x0, cy + h / 2), (x0 + w, cy)], fill=cor)


def icone_pause(d, cx, cy, tam, cor):
    h = tam * 0.68
    w = tam * 0.20
    gap = tam * 0.14
    d.rectangle((cx - gap - w, cy - h / 2, cx - gap, cy + h / 2), fill=cor)
    d.rectangle((cx + gap, cy - h / 2, cx + gap + w, cy + h / 2), fill=cor)


def icone_skip(d, cx, cy, tam, cor, anterior):
    """Barra + triangulo (skip_previous / skip_next)."""
    h = tam * 0.58
    w = h * 0.82
    if anterior:
        d.rectangle((cx - w * 0.60, cy - h / 2, cx - w * 0.44, cy + h / 2), fill=cor)
        d.polygon([(cx + w * 0.48, cy - h / 2), (cx + w * 0.48, cy + h / 2),
                   (cx - w * 0.28, cy)], fill=cor)
    else:
        d.polygon([(cx - w * 0.48, cy - h / 2), (cx - w * 0.48, cy + h / 2),
                   (cx + w * 0.28, cy)], fill=cor)
        d.rectangle((cx + w * 0.44, cy - h / 2, cx + w * 0.60, cy + h / 2), fill=cor)


def coracao(d, cx, cy, tam, cor):
    w = int(tam * 1.7)
    h = int(tam * 1.5)
    x0, y0 = cx - w // 2, cy - h // 2 + int(tam * 0.25)
    d.ellipse((x0, y0, x0 + w // 2, y0 + h // 2), fill=cor)
    d.ellipse((x0 + w // 2, y0, x0 + w, y0 + h // 2), fill=cor)
    d.polygon([(x0 + 2, y0 + h // 2 - 1), (x0 + w - 2, y0 + h // 2 - 1), (cx, y0 + h)], fill=cor)


def desenhar_visualizador(img, viz, x0, y0, x1, y1):
    """As 56 colunas espelhadas, com o gradiente e os picos (como o app).

    Desenhado numa camada RGBA à parte: a "pista" apagada usa alpha de
    verdade (no canvas RGB da tela o alpha seria ignorado).
    """
    largura = x1 - x0
    altura = y1 - y0
    gap = dp(1.5)
    barra_w = (largura - gap * (COLUMNS - 1)) / COLUMNS
    min_h = dp(2)
    pico_h = dp(2)
    raio = barra_w / 2

    camada = Image.new("RGBA", (largura, altura), (0, 0, 0, 0))
    d = ImageDraw.Draw(camada)

    # o gradiente dourado e desenhado uma vez, num recorte
    g = Image.new("RGB", (largura, altura), BAR_BOTTOM)
    gd = ImageDraw.Draw(g)
    gradiente_vertical(gd, 0, 0, largura, altura, BAR_TOP, BAR_MID, BAR_BOTTOM)

    for i in range(BARS):
        esq = (BARS - 1 - i) * (barra_w + gap)
        dir = (BARS + i) * (barra_w + gap)

        for x in (esq, dir):
            # grade apagada (a "pista") — onde as barras nascem
            d.rounded_rectangle((x, y1 - y0 - min_h, x + barra_w, y1 - y0),
                                radius=raio, fill=IDLE_BAR)
        # barra viva (as DUAS colunas do espelho, cada uma no seu lugar)
        nivel = viz.display[i]
        if nivel > 0.01:
            barra_h = min_h + (altura - min_h) * nivel
            bh = int(barra_h)
            for x in (esq, dir):
                bx = int(x)
                pedaco = g.crop((bx, altura - bh, bx + int(barra_w), altura))
                mascara = Image.new("L", pedaco.size, 0)
                ImageDraw.Draw(mascara).rounded_rectangle(
                    (0, 0, pedaco.size[0] - 1, pedaco.size[1] - 1), radius=raio, fill=255)
                camada.paste(pedaco, (bx, altura - bh), mascara)
        # pontinho de pico
        if viz.peaks[i] > 0.02:
            pico_y = altura - min_h - (altura - min_h) * viz.peaks[i] - pico_h
            for x in (esq, dir):
                d.rounded_rectangle((x, pico_y, x + barra_w, pico_y + pico_h),
                                    radius=pico_h / 2, fill=PEAK)

    img.paste(camada, (x0, y0), camada)


def desenhar_switch(d, x, y, w, h, ligado):
    """MaterialSwitch do app, no estado ligado."""
    raio = h / 2
    if ligado:
        d.rounded_rectangle((x, y, x + w, y + h), radius=raio, fill=PRIMARY)
        d.ellipse((x + w - h, y, x + w, y + h), fill=ON_BG)
    else:
        d.rounded_rectangle((x, y, x + w, y + h), radius=raio, fill=STROKE)
        d.ellipse((x, y, x + h, y + h), fill=ON_SURFACE_VAR)


def fmt_ms(ms):
    s = int(ms // 1000)
    return f"{s // 60}:{s % 60:02d}"


def quadro(viz, t, tocando, progresso):
    """Monta um frame do player de audio."""
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)

    # ---------- topo: fechar + coracao ----------
    d.line((dp(20) - 7, dp(20) - 7, dp(20) + 7, dp(20) + 7), fill=ON_BG, width=3)
    d.line((dp(20) - 7, dp(20) + 7, dp(20) + 7, dp(20) - 7), fill=ON_BG, width=3)
    coracao(d, W - dp(20), dp(20), dp(10), ON_BG)

    # ---------- card de arte 16:9 (gradiente 135 como o bg_album_art) ----------
    pad = dp(24)
    ax0, ay0 = pad, dp(52)
    ax1 = W - pad
    ay1 = ay0 + int((ax1 - ax0) * 9 / 16)
    arte = Image.new("RGB", (ax1 - ax0, ay1 - ay0))
    ad = ImageDraw.Draw(arte)
    w, h = arte.size
    for y in range(h):
        for x in range(0, w, 4):
            # 135 graus: do topo-esquerda pro baixo-direita
            t = (x / w * 0.35 + y / h * 0.65)
            cor = tuple(int(ICON_BG_START[i] + (ICON_BG_END[i] - ICON_BG_START[i]) * t)
                        for i in range(3))
            ad.rectangle((x, y, x + 3, y), fill=cor)
    # cantos 32dp
    mascara = Image.new("L", arte.size, 0)
    ImageDraw.Draw(mascara).rounded_rectangle((0, 0, w - 1, h - 1), radius=dp(32), fill=255)
    img.paste(arte, (ax0, ay0), mascara)
    # a nota musical no centro (o ic_music_note do app)
    nota_musical(d, (ax0 + ax1) / 2, (ay0 + ay1) / 2, dp(38), (255, 255, 255))

    # ---------- titulo ----------
    f_titulo = fonte(F_TITULO, sp(16))
    y = ay1 + dp(22)
    d.text((W / 2, y), "Neon Dourado", font=f_titulo, fill=ON_BG, anchor="ma")

    # ---------- pastilha "Letra" ----------
    f_pill = fonte(F_TEXTO, sp(13))
    largura_pill = dp(96)
    altura_pill = dp(34)
    px0 = (W - largura_pill) / 2
    py0 = y + dp(22) + dp(12)
    d.rounded_rectangle((px0, py0, px0 + largura_pill, py0 + altura_pill),
                        radius=dp(20), fill=SURFACE, outline=STROKE, width=max(1, dp(1)))
    microfone(d, px0 + dp(20), py0 + altura_pill / 2, dp(15), PRIMARY)
    d.text((px0 + dp(36), py0 + altura_pill / 2), "Letra", font=f_pill, fill=ON_BG, anchor="lm")

    # ---------- visualizador ----------
    vy0 = py0 + altura_pill + dp(18)
    vy1 = vy0 + dp(72)
    desenhar_visualizador(img, viz, pad, vy0, W - pad, vy1)

    # ---------- barra de progresso + tempos ----------
    f_tempo = fonte(F_TEXTO, sp(12))
    by = vy1 + dp(16)
    d.rounded_rectangle((pad, by, W - pad, by + dp(3)), radius=2, fill=STROKE)
    px = pad + int((W - 2 * pad) * progresso)
    d.rounded_rectangle((pad, by, px, by + dp(3)), radius=2, fill=PRIMARY)
    r = dp(5)
    d.ellipse((px - r, by + dp(1.5) - r, px + r, by + dp(1.5) + r), fill=PRIMARY)
    d.text((pad, by + dp(9)), fmt_ms(progresso * 238_000), font=f_tempo, fill=ON_SURFACE_VAR, anchor="la")
    d.text((W - pad, by + dp(9)), "3:58", font=f_tempo, fill=ON_SURFACE_VAR, anchor="ra")

    # ---------- transporte ----------
    ty = by + dp(9) + dp(18)
    cy = ty + dp(38)
    icone_skip(d, W / 2 - dp(72), cy, dp(26), ON_BG, anterior=True)
    icone_skip(d, W / 2 + dp(72), cy, dp(26), ON_BG, anterior=False)
    # play/pause: disco dourado cheio (bg_play_circle)
    d_play = dp(76)
    d.ellipse((W / 2 - d_play / 2, cy - d_play / 2, W / 2 + d_play / 2, cy + d_play / 2),
              fill=PRIMARY)
    if tocando:
        icone_pause(d, W / 2, cy, d_play * 0.40, BG)
    else:
        icone_play(d, W / 2, cy, d_play * 0.40, BG)

    # ---------- reproducao automatica ----------
    ay = cy + d_play / 2 + dp(10)
    f_auto = fonte(F_TEXTO, sp(13))
    # o texto fica a ESQUERDA do switch (como no app: nome + switch na
    # mesma linha, centralizados)
    largura_texto = d.textlength("Reprodução automática", font=f_auto)
    sw_w, sw_h = dp(42), dp(23)
    gap = dp(10)
    total = largura_texto + gap + sw_w
    tx0 = (W - total) / 2
    d.text((tx0, ay + sw_h / 2), "Reprodução automática",
           font=f_auto, fill=ON_SURFACE_VAR, anchor="lm")
    desenhar_switch(d, tx0 + largura_texto + gap, ay, sw_w, sw_h, ligado=True)

    return img


def principal():
    """Simula a 60fps (como o app) e amostra os quadros do GIF."""
    viz = Visualizador()
    frames = []
    passos_por_frame = SIM_FPS // FPS
    duracao_ms = 238_000

    # a musica toca de 0 a 4.2s e depois pausa (as barras caem sozinhas)
    t_ms = 0
    total_ms = int(DURACAO * 1000)
    for i in range(N_FRAMES):
        alvo_ms = i * 1000 / FPS
        # avanca a simulacao ate o alvo
        while t_ms < alvo_ms:
            tocando = t_ms < 4200
            viz.passo(t_ms, tocando)
            t_ms += 1000 / SIM_FPS
        tocando = alvo_ms < 4200
        prog = min(1.0, (0.18 + 0.34 * (alvo_ms / 4200.0)) if tocando
                   else (0.52 + 0.0 * 1))
        frames.append(quadro(viz, alvo_ms / 1000.0, tocando, prog))
    return frames


def main():
    raiz = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    destino = os.path.join(raiz, "docs", "player-audio-demo.gif")
    os.makedirs(os.path.dirname(destino), exist_ok=True)

    frames = principal()
    frames = [f.convert("P", palette=Image.ADAPTIVE, colors=128) for f in frames]
    frames[0].save(
        destino,
        save_all=True,
        append_images=frames[1:],
        duration=int(1000 / FPS),
        loop=0,
        optimize=True,
        disposal=2,
    )
    tamanho = os.path.getsize(destino) / 1024
    print(f"gerado: {destino} ({len(frames)} frames, {tamanho:.0f} KB)")


if __name__ == "__main__":
    main()
