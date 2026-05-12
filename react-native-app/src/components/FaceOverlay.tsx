import React, { useEffect, useState, useRef, useCallback } from 'react';
import { View, StyleSheet, DeviceEventEmitter } from 'react-native';
import Svg, { Rect, G, Path, Ellipse, Circle, Line, Text as SvgText } from 'react-native-svg';
import gsap from 'gsap';
import { interpolate } from 'flubber';

// ═══════════════════════════════════════════════════════════════════════════════
// KAWAII MASCOT — SVG + Flubber (pixel-perfect match with web RobotFace.tsx)
// ═══════════════════════════════════════════════════════════════════════════════

type MorphFn = (t: number) => string;
const mkMorph = (a: string, b: string): MorphFn => interpolate(a, b, { maxSegmentLength: 6 });
const lerp = (a: number, b: number, t: number) => a + (b - a) * t;
const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));

// ─── Anchors (same as web) ───
const A = { eyeL: { x: 162, y: 125 }, eyeR: { x: 238, y: 125 }, mouth: { x: 200, y: 176 } };
const CX = 200, CY = 150;

// ─── SVG Paths (from paths.ts — identical to web) ───
const EYE: Record<string, string> = {
  idle:       "M -15 -24 C -15 -34 15 -34 15 -24 L 15 24 C 15 34 -15 34 -15 24 Z",
  happy:      "M -20 8 C -20 -12 -8 -16 0 -16 C 8 -16 20 -12 20 8 C 12 5 -12 5 -20 8 Z",
  sad:        "M -14 -20 C -14 -30 14 -30 14 -20 L 14 20 C 14 30 -14 30 -14 20 Z",
  thinkingS:  "M -12 -18 C -12 -26 12 -26 12 -18 L 12 18 C 12 26 -12 26 -12 18 Z",
  thinkingL:  "M -15 -24 C -15 -34 15 -34 15 -24 L 15 24 C 15 34 -15 34 -15 24 Z",
  listening:  "M -17 -26 C -17 -36 17 -36 17 -26 L 17 26 C 17 36 -17 36 -17 26 Z",
  speaking:   "M -16 -22 C -16 -32 16 -32 16 -22 L 16 22 C 16 32 -16 32 -16 22 Z",
  processing: "M -12 -22 C -12 -30 12 -30 12 -22 L 12 22 C 12 30 -12 30 -12 22 Z",
  sleeping:   "M -18 4 C -14 -8 14 -8 18 4 C 12 1 -12 1 -18 4 Z",
  surprised:  "M -26 0 C -26 -28 26 -28 26 0 C 26 28 -26 28 -26 0 Z",
};
const EYE_BLINK = "M -16 -2 C -16 -5 16 -5 16 -2 L 16 2 C 16 5 -16 5 -16 2 Z";

const MOUTH: Record<string, string> = {
  idle:       "M -10 0 C -6 0 6 0 10 0 C 6 6 -6 6 -10 0 Z",
  happy:      "M -16 -1 C -10 -1 -5 5 0 5 C 5 5 10 -1 16 -1 C 12 9 -12 9 -16 -1 Z",
  sad:        "M -9 3 C -5 3 5 3 9 3 C 5 -3 -5 -3 -9 3 Z",
  thinking:   "M -8 1 C -4 -2 4 2 8 -1 C 5 4 -5 5 -8 1 Z",
  listening:  "M -8 0 C -4 -1 4 -1 8 0 C 4 4 -4 4 -8 0 Z",
  speaking:   "M -11 -2 C -6 -6 6 -6 11 -2 C 6 6 -6 6 -11 -2 Z",
  processing: "M -5 0 C -5 -3 5 -3 5 0 C 5 3 -5 3 -5 0 Z",
  sleeping:   "M -5 0 C -5 -4 5 -4 5 0 C 5 4 -5 4 -5 0 Z",
  surprised:  "M -9 0 C -9 -9 9 -9 9 0 C 9 9 -9 9 -9 0 Z",
};
const MOUTH_SPEAK_OPEN = "M -13 -4 C -7 -10 7 -10 13 -4 C 7 8 -7 8 -13 -4 Z";

// ─── Emotion Poses ───
interface Pose { eyeL: string; eyeR: string; mouth: string; headTilt: number; headNod: number; breathAmp: number; }
const POSES: Record<string, Pose> = {
  idle:       { eyeL: EYE.idle,       eyeR: EYE.idle,       mouth: MOUTH.idle,       headTilt: 0,  headNod: 0,  breathAmp: 1   },
  happy:      { eyeL: EYE.happy,      eyeR: EYE.happy,      mouth: MOUTH.happy,      headTilt: 2,  headNod:-4,  breathAmp: 1.4 },
  sad:        { eyeL: EYE.sad,        eyeR: EYE.sad,        mouth: MOUTH.sad,        headTilt:-3,  headNod: 5,  breathAmp: 0.7 },
  thinking:   { eyeL: EYE.thinkingS,  eyeR: EYE.thinkingL,  mouth: MOUTH.thinking,   headTilt: 5,  headNod:-1,  breathAmp: 0.8 },
  listening:  { eyeL: EYE.listening,  eyeR: EYE.listening,  mouth: MOUTH.listening,  headTilt: 0,  headNod: 0,  breathAmp: 0.9 },
  speaking:   { eyeL: EYE.speaking,   eyeR: EYE.speaking,   mouth: MOUTH.speaking,   headTilt: 1,  headNod:-1,  breathAmp: 1.1 },
  processing: { eyeL: EYE.processing, eyeR: EYE.processing, mouth: MOUTH.processing, headTilt: 0,  headNod: 0,  breathAmp: 0.6 },
  sleeping:   { eyeL: EYE.sleeping,   eyeR: EYE.sleeping,   mouth: MOUTH.sleeping,   headTilt:-3,  headNod: 8,  breathAmp: 1.8 },
  surprised:  { eyeL: EYE.surprised,  eyeR: EYE.surprised,  mouth: MOUTH.surprised,  headTilt: 0,  headNod:-6,  breathAmp: 0.4 },
};

// ─── Transition Timing (same as web) ───
interface Timing { anticipation: number; action: number; overshoot: number; settle: number; squashAmt: number; overshootAmt: number; }
const TIMINGS: Record<string, Timing> = {
  bouncy: { anticipation: 0.06, action: 0.22, overshoot: 0.16, settle: 0.3, squashAmt: 0.12, overshootAmt: 0.18 },
  poppy:  { anticipation: 0.04, action: 0.14, overshoot: 0.12, settle: 0.22, squashAmt: 0.15, overshootAmt: 0.22 },
  soft:   { anticipation: 0.08, action: 0.35, overshoot: 0.18, settle: 0.4, squashAmt: 0.06, overshootAmt: 0.1 },
  wobbly: { anticipation: 0.1, action: 0.2, overshoot: 0.25, settle: 0.5, squashAmt: 0.18, overshootAmt: 0.25 },
};
const EMO_TIMING: Record<string, string> = {
  idle:'soft', happy:'poppy', sad:'soft', thinking:'bouncy', listening:'bouncy',
  speaking:'poppy', processing:'bouncy', sleeping:'soft', surprised:'wobbly',
};

// ─── Backward-compat aliases ───
const ALIAS: Record<string, string> = {
  neutral:'idle', calm:'idle', sceptic:'thinking', confused:'thinking', suspicious:'thinking',
  tired:'sleeping', sleepy:'sleeping', broken:'sad', denying:'sad',
  in_love:'happy', wink:'happy', angry:'surprised', afraid:'surprised',
  disgusted:'surprised', crazy:'surprised', interested:'listening',
};
const resolveEmo = (name: string): string => {
  const k = name.toLowerCase().replace(/\s+/g, '_');
  if (POSES[k]) return k;
  if (ALIAS[k] && POSES[ALIAS[k]]) return ALIAS[k];
  return 'idle';
};

// ─── Component ───
const FaceOverlay = () => {
  const [visible, setVisible] = useState(false);
  const [, setTick] = useState(0);
  const tick = useCallback(() => setTick(n => n + 1), []);

  const sRef = useRef({
    eyeL: POSES.idle.eyeL, eyeR: POSES.idle.eyeR, mouth: POSES.idle.mouth,
    headTilt: 0, headNod: 0, squashX: 1, squashY: 1, opacity: 0,
  });
  const morphs = useRef<{ eyeL: MorphFn; eyeR: MorphFn; mouth: MorphFn } | null>(null);
  const blinkMorphs = useRef<{ cL: MorphFn; cR: MorphFn; oL: MorphFn; oR: MorphFn } | null>(null);
  const speakMorph = useRef<{ open: MorphFn } | null>(null);
  const targetPose = useRef<Pose>(POSES.idle);
  const emoName = useRef('idle');
  const isSpeaking = useRef(false);
  const isBlinking = useRef(false);
  const speakPhase = useRef(0);
  const timeRef = useRef(0);
  const hideT = useRef<any>(null);
  const tl = useRef<gsap.core.Timeline | null>(null);
  const blinkTw = useRef<any>(null);

  // ─── Render loop via GSAP ticker ───
  useEffect(() => {
    const onTick = () => { timeRef.current = gsap.ticker.time; if (visible) tick(); };
    gsap.ticker.add(onTick);
    return () => gsap.ticker.remove(onTick);
  }, [visible]);

  // ─── Blink ───
  const scheduleBlink = useCallback(() => {
    blinkTw.current?.kill();
    const delay = 2.0 + Math.random() * 3.0;
    blinkTw.current = gsap.delayedCall(delay, () => {
      if (isBlinking.current || !blinkMorphs.current) return;
      if (emoName.current === 'sleeping') { scheduleBlink(); return; }
      isBlinking.current = true;
      const s = sRef.current;
      const { cL, cR, oL, oR } = blinkMorphs.current;
      const bp = { t: 0 };
      gsap.timeline({ onComplete() { isBlinking.current = false; scheduleBlink(); } })
        .to(bp, { t: 1, duration: 0.05, ease: 'power3.in', onUpdate() { s.eyeL = cL(bp.t); s.eyeR = cR(bp.t); } })
        .to({}, { duration: 0.03 })
        .to(bp, { t: 0, duration: 0.18, ease: 'elastic.out(1.1, 0.4)', onUpdate() { s.eyeL = oL(1 - bp.t); s.eyeR = oR(1 - bp.t); } });
    });
  }, []);

  // ─── Speaking mouth oscillation ───
  useEffect(() => {
    const onSpeak = () => {
      if (!isSpeaking.current || !speakMorph.current) return;
      speakPhase.current += 0.07;
      const t = speakPhase.current;
      const wave = 0.3 + Math.sin(t * 4.2) * 0.28 + Math.sin(t * 8.5) * 0.15 + Math.random() * 0.04;
      sRef.current.mouth = speakMorph.current.open(clamp(wave, 0, 1) * 0.65);
    };
    gsap.ticker.add(onSpeak);
    return () => gsap.ticker.remove(onSpeak);
  }, []);

  // ─── Mouth decay when not speaking ───
  useEffect(() => {
    const onDecay = () => {
      if (isSpeaking.current) return;
      const s = sRef.current;
      const target = targetPose.current;
      if (s.mouth !== target.mouth) {
        try { s.mouth = mkMorph(s.mouth, target.mouth)(0.18); } catch { s.mouth = target.mouth; }
      }
    };
    gsap.ticker.add(onDecay);
    return () => gsap.ticker.remove(onDecay);
  }, []);

  // ─── Emotion event handler ───
  useEffect(() => {
    const sub = DeviceEventEmitter.addListener('onEmotionAction', (ev) => {
      const name = resolveEmo(ev.emotion || 'idle');
      const fromPose = targetPose.current;
      const toPose = POSES[name] ?? POSES.idle;
      targetPose.current = toPose;
      emoName.current = name;
      isSpeaking.current = name === 'speaking';
      setVisible(true);
      if (hideT.current) clearTimeout(hideT.current);
      tl.current?.kill();
      blinkTw.current?.kill();

      morphs.current = { eyeL: mkMorph(fromPose.eyeL, toPose.eyeL), eyeR: mkMorph(fromPose.eyeR, toPose.eyeR), mouth: mkMorph(fromPose.mouth, toPose.mouth) };
      blinkMorphs.current = { cL: mkMorph(toPose.eyeL, EYE_BLINK), cR: mkMorph(toPose.eyeR, EYE_BLINK), oL: mkMorph(EYE_BLINK, toPose.eyeL), oR: mkMorph(EYE_BLINK, toPose.eyeR) };
      speakMorph.current = { open: mkMorph(toPose.mouth, MOUTH_SPEAK_OPEN) };

      const s = sRef.current;
      const mp = { t: 0 };
      const tk = EMO_TIMING[name] ?? 'bouncy';
      const tm = TIMINGS[tk] ?? TIMINGS.bouncy;

      tl.current = gsap.timeline()
        .to(s, { opacity: 1, duration: 0.2 }, 0)
        .to(s, { squashX: 1 + tm.squashAmt, squashY: 1 - tm.squashAmt, duration: tm.anticipation, ease: 'power2.in' })
        .to(mp, {
          t: 1, duration: tm.action, ease: 'back.out(1.4)',
          onUpdate() {
            const mt = mp.t;
            if (morphs.current) {
              s.eyeL = morphs.current.eyeL(mt);
              s.eyeR = morphs.current.eyeR(mt);
              if (!isSpeaking.current) s.mouth = morphs.current.mouth(mt);
            }
            s.headTilt = lerp(fromPose.headTilt, toPose.headTilt, mt);
            s.headNod = lerp(fromPose.headNod, toPose.headNod, mt);
          },
        }, tm.anticipation * 0.4)
        .to(s, { squashX: 1 - tm.overshootAmt * 0.6, squashY: 1 + tm.overshootAmt * 0.6, duration: tm.overshoot, ease: 'power2.out' })
        .to(s, { squashX: 1, squashY: 1, duration: tm.settle, ease: 'elastic.out(1.2, 0.35)' });

      scheduleBlink();

      hideT.current = setTimeout(() => {
        blinkTw.current?.kill();
        gsap.to(s, { opacity: 0, duration: 0.4, onComplete: () => setVisible(false) });
      }, 5000);
    });

    return () => { sub.remove(); if (hideT.current) clearTimeout(hideT.current); tl.current?.kill(); blinkTw.current?.kill(); };
  }, []);

  // ─── RENDER ───
  if (!visible && sRef.current.opacity === 0) return null;

  const s = sRef.current;
  const t = timeRef.current;
  const emo = emoName.current;

  const floatY = Math.sin(t * 1.6) * 3.5 + Math.sin(t * 2.3) * 1.5;
  const floatTilt = Math.sin(t * 1.1) * 0.8;
  const breathAmp = targetPose.current.breathAmp ?? 1;
  const speakNod = isSpeaking.current ? Math.sin(t * 5.5) * 1.2 + Math.sin(t * 8.3) * 0.6 : 0;

  const headY = s.headNod + floatY * breathAmp * 0.5 + speakNod;
  const headTilt = s.headTilt + floatTilt;
  const headTx = `translate(${CX}, ${CY + headY}) rotate(${headTilt}) scale(${s.squashX}, ${s.squashY}) translate(${-CX}, ${-CY})`;

  const showBlush = emo === 'happy' || emo === 'speaking' || emo === 'listening';
  const blushOp = emo === 'happy' ? 0.35 : 0.2;

  // Sparkle helpers
  const sp1 = Math.sin(t * 3) * 0.4 + 0.6;
  const sp2 = Math.sin(t * 3 + 2) * 0.4 + 0.6;
  const sp3 = Math.sin(t * 2.5 + 1) * 0.4 + 0.6;
  const spy1 = Math.sin(t * 2) * 3;
  const spy2 = Math.sin(t * 2.3 + 1) * 3;

  // Zzz helpers
  const zCycle = (t * 0.6) % 3;
  const z1y = -zCycle * 14, z1o = Math.max(0, 1 - zCycle * 0.35);
  const z2y = -(((zCycle + 1) % 3) * 14), z2o = Math.max(0, 1 - ((zCycle + 1) % 3) * 0.35);
  const z3y = -(((zCycle + 2) % 3) * 14), z3o = Math.max(0, 1 - ((zCycle + 2) % 3) * 0.35);

  // Burst helper
  const burstPulse = 0.7 + Math.sin(t * 6) * 0.3;

  // Dots helpers
  const dy1 = Math.sin(t * 5) * 5, dy2 = Math.sin(t * 5 + 1.3) * 5, dy3 = Math.sin(t * 5 + 2.6) * 5;
  const dop1 = 0.4 + Math.sin(t * 5) * 0.3, dop2 = 0.4 + Math.sin(t * 5 + 1.3) * 0.3, dop3 = 0.4 + Math.sin(t * 5 + 2.6) * 0.3;

  return (
    <View style={[sty.root, { opacity: s.opacity }]}>
      <Svg viewBox="0 0 400 300" style={sty.svg}>
        <Rect width={400} height={300} fill="#060d0f" />
        <G transform={headTx}>
          {/* Left eye */}
          <G transform={`translate(${A.eyeL.x}, ${A.eyeL.y})`}>
            <Path d={s.eyeL} fill="#ffffff" />
          </G>
          {/* Right eye */}
          <G transform={`translate(${A.eyeR.x}, ${A.eyeR.y})`}>
            <Path d={s.eyeR} fill="#ffffff" />
          </G>
          {/* Mouth */}
          <G transform={`translate(${A.mouth.x}, ${A.mouth.y})`}>
            <Path d={s.mouth} fill="none" stroke="#ffffff" strokeWidth={2.5} strokeLinecap="round" strokeLinejoin="round" />
          </G>
          {/* Blush */}
          {showBlush && (
            <G opacity={blushOp}>
              <Ellipse cx={A.eyeL.x - 6} cy={A.eyeL.y + 22} rx={9} ry={5} fill="#ff8ab5" />
              <Ellipse cx={A.eyeR.x + 6} cy={A.eyeR.y + 22} rx={9} ry={5} fill="#ff8ab5" />
            </G>
          )}
          {/* Happy sparkles */}
          {emo === 'happy' && (
            <G opacity={0.7}>
              <G transform={`translate(${A.eyeR.x + 32}, ${A.eyeL.y - 18 + spy1}) scale(${sp1})`}>
                <Line x1={-4} y1={0} x2={4} y2={0} stroke="#ffffff" strokeWidth={1.5} strokeLinecap="round" />
                <Line x1={0} y1={-4} x2={0} y2={4} stroke="#ffffff" strokeWidth={1.5} strokeLinecap="round" />
              </G>
              <G transform={`translate(${A.eyeL.x - 28}, ${A.eyeL.y - 10 + spy2}) scale(${sp2})`}>
                <Line x1={-3} y1={0} x2={3} y2={0} stroke="#ffffff" strokeWidth={1.2} strokeLinecap="round" />
                <Line x1={0} y1={-3} x2={0} y2={3} stroke="#ffffff" strokeWidth={1.2} strokeLinecap="round" />
              </G>
              <G transform={`translate(${A.eyeR.x + 18}, ${A.mouth.y + 6}) scale(${sp3})`}>
                <Line x1={-2.5} y1={0} x2={2.5} y2={0} stroke="#ffffff" strokeWidth={1} strokeLinecap="round" />
                <Line x1={0} y1={-2.5} x2={0} y2={2.5} stroke="#ffffff" strokeWidth={1} strokeLinecap="round" />
              </G>
            </G>
          )}
          {/* Sleeping Zzz */}
          {emo === 'sleeping' && (
            <G transform={`translate(${A.eyeR.x + 28}, ${A.eyeR.y - 12})`}>
              <SvgText x={0} y={z1y} fill="#ffffff" opacity={z1o * 0.7} fontSize={14} fontWeight="bold">Z</SvgText>
              <SvgText x={10} y={z2y - 4} fill="#ffffff" opacity={z2o * 0.5} fontSize={11} fontWeight="bold">z</SvgText>
              <SvgText x={18} y={z3y - 8} fill="#ffffff" opacity={z3o * 0.35} fontSize={9} fontWeight="bold">z</SvgText>
            </G>
          )}
          {/* Surprised burst */}
          {emo === 'surprised' && (
            <G opacity={burstPulse * 0.5}>
              <Line x1={A.eyeL.x - 16} y1={A.eyeL.y - 42} x2={A.eyeL.x - 22} y2={A.eyeL.y - 52} stroke="#ffffff" strokeWidth={1.5} strokeLinecap="round" />
              <Line x1={200} y1={A.eyeL.y - 48} x2={200} y2={A.eyeL.y - 58} stroke="#ffffff" strokeWidth={1.5} strokeLinecap="round" />
              <Line x1={A.eyeR.x + 16} y1={A.eyeR.y - 42} x2={A.eyeR.x + 22} y2={A.eyeR.y - 52} stroke="#ffffff" strokeWidth={1.5} strokeLinecap="round" />
            </G>
          )}
          {/* Processing dots */}
          {emo === 'processing' && (
            <G transform={`translate(${A.mouth.x}, ${A.mouth.y + 20})`}>
              <Circle cx={-12} cy={dy1} r={3} fill="#ffffff" opacity={dop1} />
              <Circle cx={0} cy={dy2} r={3} fill="#ffffff" opacity={dop2} />
              <Circle cx={12} cy={dy3} r={3} fill="#ffffff" opacity={dop3} />
            </G>
          )}
        </G>
      </Svg>
    </View>
  );
};

const sty = StyleSheet.create({
  root: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: '#060d0f',
    zIndex: 9999,
    elevation: 9999,
  },
  svg: {
    flex: 1,
  },
});

export default FaceOverlay;
