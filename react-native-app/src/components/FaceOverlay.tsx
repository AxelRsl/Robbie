import React, { useEffect, useState, useRef, useCallback } from 'react';
import { View, Text, StyleSheet, DeviceEventEmitter, Dimensions } from 'react-native';
import gsap from 'gsap';

const { width: W } = Dimensions.get('window');

// ─── Per-eye property defaults ───
const D = {
  // Bar 1: primary solid rounded rect
  b1o: 1, b1w: 55, b1h: 75, b1r: 0, b1br: 24, b1tx: 0, b1ty: 0,
  // Bar 2: secondary bar (for X / chevron)
  b2o: 0, b2w: 55, b2h: 14, b2r: 0, b2br: 7, b2tx: 0, b2ty: 0,
  // Arc: solid filled half-pill (NO border trick)
  ao: 0, aw: 70, ah: 30, abrTL: 35, abrTR: 35, abrBL: 4, abrBR: 4,
  // Ring: circle outline
  ro: 0, rs: 60, rbw: 11,
  // Dot: small solid circle (spiral center)
  dto: 0,
  // Heart
  ho: 0,
  // Color
  cr: 0, cg: 255, cb: 255,
};

type EC = typeof D;
const mk = (o: Partial<EC>): EC => ({ ...D, ...o });
const OFF = { b1o: 0, b2o: 0 }; // hide bars

// ─── 9 Kawaii Emotions (Dasai Mochi style) ───
const EM: Record<string, { L: EC; R: EC }> = {
  // IDLE: tall rounded pill capsules (resting cute)
  idle: {
    L: mk({ b1o: 1, b1w: 50, b1h: 72, b1br: 25 }),
    R: mk({ b1o: 1, b1w: 50, b1h: 72, b1br: 25 }),
  },
  // HAPPY: ^_^ kawaii arcs (top half-pills)
  happy: {
    L: mk({ ...OFF, ao: 1, aw: 65, ah: 28, abrTL: 33, abrTR: 33, abrBL: 4, abrBR: 4 }),
    R: mk({ ...OFF, ao: 1, aw: 65, ah: 28, abrTL: 33, abrTR: 33, abrBL: 4, abrBR: 4 }),
  },
  // SAD: droopy smaller pills, slightly tilted inward
  sad: {
    L: mk({ b1o: 1, b1w: 44, b1h: 60, b1br: 22, b1r: 5 }),
    R: mk({ b1o: 1, b1w: 44, b1h: 60, b1br: 22, b1r: -5 }),
  },
  // THINKING: asymmetric — one smaller, one normal (curious tilt)
  thinking: {
    L: mk({ b1o: 1, b1w: 38, b1h: 50, b1br: 19 }),
    R: mk({ b1o: 1, b1w: 50, b1h: 72, b1br: 25 }),
  },
  // LISTENING: slightly wider, taller pills (alert and attentive)
  listening: {
    L: mk({ b1o: 1, b1w: 54, b1h: 78, b1br: 27 }),
    R: mk({ b1o: 1, b1w: 54, b1h: 78, b1br: 27 }),
  },
  // SPEAKING: lively pills, slightly wider (engaged)
  speaking: {
    L: mk({ b1o: 1, b1w: 52, b1h: 68, b1br: 26 }),
    R: mk({ b1o: 1, b1w: 52, b1h: 68, b1br: 26 }),
  },
  // PROCESSING: narrow focused pills
  processing: {
    L: mk({ b1o: 1, b1w: 40, b1h: 64, b1br: 20 }),
    R: mk({ b1o: 1, b1w: 40, b1h: 64, b1br: 20 }),
  },
  // SLEEPING: relaxed crescent arcs (bottom half-pills = peaceful closed eyes)
  sleeping: {
    L: mk({ ...OFF, ao: 1, aw: 60, ah: 20, abrTL: 4, abrTR: 4, abrBL: 30, abrBR: 30 }),
    R: mk({ ...OFF, ao: 1, aw: 60, ah: 20, abrTL: 4, abrTR: 4, abrBL: 30, abrBR: 30 }),
  },
  // SURPRISED: big round circles (cartoon shock)
  surprised: {
    L: mk({ ...OFF, ro: 1, rs: 70, rbw: 12 }),
    R: mk({ ...OFF, ro: 1, rs: 70, rbw: 12 }),
  },
};

// Backward-compat aliases for old emotion names from bot
EM.neutral = EM.idle;
EM.calm = EM.idle;
EM.sceptic = EM.thinking;
EM.confused = EM.thinking;
EM.suspicious = EM.thinking;
EM.tired = EM.sleeping;
EM.sleepy = EM.sleeping;
EM.broken = EM.sad;
EM.in_love = EM.happy;
EM.wink = EM.happy;
EM.denying = EM.sad;
EM.angry = EM.surprised;
EM.afraid = EM.surprised;
EM.disgusted = EM.surprised;
EM.interested = EM.listening;
EM.crazy = EM.surprised;

// ─── Build flat GSAP state ───
const buildState = () => {
  const s: any = { op: 0, blink: 1 };
  for (const k of Object.keys(D)) {
    s[`L_${k}`] = D[k as keyof EC];
    s[`R_${k}`] = D[k as keyof EC];
  }
  return s;
};

// ─── Component ───
const FaceOverlay = () => {
  const [visible, setVisible] = useState(false);
  const [, bump] = useState(0);
  const tick = useCallback(() => bump(n => n + 1), []);

  const hideT = useRef<any>(null);
  const blinkT = useRef<any>(null);
  const pulseT = useRef<any>(null);
  const st = useRef(buildState()).current;
  const glow = useRef({ v: 1 }).current;

  // Blink loop
  const doBlink = useCallback(() => {
    blinkT.current = setTimeout(() => {
      gsap.to(st, {
        blink: 0.08, duration: 0.09, yoyo: true, repeat: 1,
        onUpdate: tick,
        onComplete: () => doBlink(),
      });
    }, 2500 + Math.random() * 3000);
  }, []);

  // Glow pulse loop
  const startGlow = useCallback(() => {
    if (pulseT.current) gsap.killTweensOf(glow);
    pulseT.current = gsap.to(glow, {
      v: 1.6, duration: 1.2, yoyo: true, repeat: -1, ease: 'sine.inOut',
      onUpdate: tick,
    });
  }, []);

  useEffect(() => {
    const sub = DeviceEventEmitter.addListener('onEmotionAction', (ev) => {
      const emo = ev.emotion ? ev.emotion.toLowerCase().replace(/\s+/g, '_') : 'idle';
      const cfg = EM[emo] || EM.idle;

      setVisible(true);
      if (hideT.current) clearTimeout(hideT.current);
      if (blinkT.current) clearTimeout(blinkT.current);

      // Build target
      const tgt: any = { op: 1, blink: 1 };
      for (const k of Object.keys(D)) {
        tgt[`L_${k}`] = cfg.L[k as keyof EC];
        tgt[`R_${k}`] = cfg.R[k as keyof EC];
      }

      gsap.to(st, {
        ...tgt, duration: 0.7, ease: 'elastic.out(1,0.75)', onUpdate: tick,
      });

      startGlow();
      setTimeout(() => doBlink(), 1000);

      hideT.current = setTimeout(() => {
        if (blinkT.current) clearTimeout(blinkT.current);
        gsap.killTweensOf(glow);
        gsap.to(st, {
          op: 0, duration: 0.4, onUpdate: tick,
          onComplete: () => setVisible(false),
        });
      }, 5000);
    });

    return () => {
      sub.remove();
      clearTimeout(hideT.current);
      clearTimeout(blinkT.current);
      gsap.killTweensOf(st);
      gsap.killTweensOf(glow);
    };
  }, []);

  if (!visible && st.op === 0) return null;

  const g = (p: string, k: string): number => st[`${p}${k}`] ?? 0;
  const bk = st.blink;
  const glowMul = glow.v;

  const col = (p: string) =>
    `rgb(${Math.round(g(p, 'cr'))},${Math.round(g(p, 'cg'))},${Math.round(g(p, 'cb'))})`;

  const neon = (p: string) => ({
    shadowColor: col(p),
    shadowOpacity: Math.min(1, 0.9 * glowMul),
    shadowRadius: 22 * glowMul,
    elevation: 15,
  });

  const eye = (p: 'L_' | 'R_') => (
    <View style={s.eyeBox}>
      {/* Bar 1 */}
      <View style={[s.abs, {
        opacity: g(p, 'b1o'),
        width: g(p, 'b1w'), height: g(p, 'b1h') * bk,
        borderRadius: g(p, 'b1br'),
        backgroundColor: col(p),
        transform: [
          { translateX: g(p, 'b1tx') },
          { translateY: g(p, 'b1ty') },
          { rotate: `${g(p, 'b1r')}deg` },
        ],
        ...neon(p),
      }]} />

      {/* Bar 2 */}
      <View style={[s.abs, {
        opacity: g(p, 'b2o'),
        width: g(p, 'b2w'), height: g(p, 'b2h') * bk,
        borderRadius: g(p, 'b2br'),
        backgroundColor: col(p),
        transform: [
          { translateX: g(p, 'b2tx') },
          { translateY: g(p, 'b2ty') },
          { rotate: `${g(p, 'b2r')}deg` },
        ],
        ...neon(p),
      }]} />

      {/* Arc (SOLID filled half-pill, no border trick) */}
      <View style={[s.abs, {
        opacity: g(p, 'ao'),
        width: g(p, 'aw'), height: g(p, 'ah') * bk,
        backgroundColor: col(p),
        borderTopLeftRadius: g(p, 'abrTL'),
        borderTopRightRadius: g(p, 'abrTR'),
        borderBottomLeftRadius: g(p, 'abrBL'),
        borderBottomRightRadius: g(p, 'abrBR'),
        ...neon(p),
      }]} />

      {/* Ring */}
      <View style={[s.abs, {
        opacity: g(p, 'ro'),
        width: g(p, 'rs') * bk, height: g(p, 'rs') * bk,
        borderRadius: g(p, 'rs') / 2,
        borderWidth: g(p, 'rbw'),
        borderColor: col(p),
        backgroundColor: 'transparent',
        ...neon(p),
      }]} />

      {/* Dot (spiral center) */}
      <View style={[s.abs, {
        opacity: g(p, 'dto'),
        width: 14, height: 14 * bk,
        borderRadius: 7,
        backgroundColor: col(p),
        ...neon(p),
      }]} />

      {/* Heart */}
      <Text style={{
        position: 'absolute',
        fontSize: 58,
        opacity: g(p, 'ho'),
        color: col(p),
        textShadowColor: col(p),
        textShadowRadius: 25 * glowMul,
        transform: [{ scaleY: bk }],
      }}>♥</Text>
    </View>
  );

  return (
    <View style={[s.root, { opacity: st.op }]}>
      <View style={s.face}>
        {eye('L_')}
        {eye('R_')}
      </View>
    </View>
  );
};

const s = StyleSheet.create({
  root: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: '#010103',
    justifyContent: 'center',
    alignItems: 'center',
    zIndex: 9999,
    elevation: 9999,
  },
  face: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: W * 0.09,
  },
  eyeBox: {
    width: 110,
    height: 110,
    justifyContent: 'center',
    alignItems: 'center',
  },
  abs: {
    position: 'absolute' as const,
  },
});

export default FaceOverlay;
