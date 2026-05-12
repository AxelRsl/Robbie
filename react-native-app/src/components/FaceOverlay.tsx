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

// ─── Anchors (v2 — wider, bolder, more presence) ───
const A = { eyeL: { x: 138, y: 122 }, eyeR: { x: 262, y: 122 }, mouth: { x: 200, y: 200 } };
const CX = 200, CY = 150;

// ─── SVG Paths (from paths.ts — identical to web) ───
const EYE: Record<string, string> = {
  idle:       "M -21 -33 C -21 -47 21 -47 21 -33 L 21 33 C 21 47 -21 47 -21 33 Z",
  happy:      "M -27 10 C -27 -16 -11 -22 0 -22 C 11 -22 27 -16 27 10 C 16 6 -16 6 -27 10 Z",
  sad:        "M -19 -28 C -19 -42 19 -42 19 -28 L 19 28 C 19 42 -19 42 -19 28 Z",
  thinkingS:  "M -16 -24 C -16 -36 16 -36 16 -24 L 16 24 C 16 36 -16 36 -16 24 Z",
  thinkingL:  "M -21 -33 C -21 -47 21 -47 21 -33 L 21 33 C 21 47 -21 47 -21 33 Z",
  listening:  "M -23 -36 C -23 -50 23 -50 23 -36 L 23 36 C 23 50 -23 50 -23 36 Z",
  speaking:   "M -22 -30 C -22 -44 22 -44 22 -30 L 22 30 C 22 44 -22 44 -22 30 Z",
  processing: "M -16 -30 C -16 -42 16 -42 16 -30 L 16 30 C 16 42 -16 42 -16 30 Z",
  sleeping:   "M -24 5 C -19 -12 19 -12 24 5 C 16 1 -16 1 -24 5 Z",
  surprised:  "M -35 0 C -35 -38 35 -38 35 0 C 35 38 -35 38 -35 0 Z",
};
const EYE_BLINK = "M -22 -3 C -22 -7 22 -7 22 -3 L 22 3 C 22 7 -22 7 -22 3 Z";

const MOUTH: Record<string, string> = {
  idle:       "M -14 -3 C -14 -7 -8 -9 0 -9 C 8 -9 14 -7 14 -3 C 14 3 8 8 0 8 C -8 8 -14 3 -14 -3 Z",
  happy:      "M -22 -4 C -22 -10 -11 -12 0 -12 C 11 -12 22 -10 22 -4 C 22 6 14 14 0 14 C -14 14 -22 6 -22 -4 Z",
  sad:        "M -11 -4 C -11 -7 -6 -7 0 -7 C 6 -7 11 -7 11 -4 C 11 0 6 5 0 5 C -6 5 -11 0 -11 -4 Z",
  thinking:   "M -10 -3 C -10 -7 -4 -8 2 -8 C 7 -7 11 -6 11 -3 C 11 3 6 7 0 6 C -6 7 -10 3 -10 -3 Z",
  listening:  "M -11 -4 C -11 -8 -6 -10 0 -10 C 6 -10 11 -8 11 -4 C 11 2 6 6 0 6 C -6 6 -11 2 -11 -4 Z",
  speaking:   "M -15 -5 C -15 -11 -7 -14 0 -14 C 7 -14 15 -11 15 -5 C 15 4 8 11 0 11 C -8 11 -15 4 -15 -5 Z",
  processing: "M -7 -4 C -7 -8 7 -8 7 -4 C 7 2 4 6 0 6 C -4 6 -7 2 -7 -4 Z",
  sleeping:   "M -7 -4 C -7 -8 7 -8 7 -4 C 7 3 4 7 0 7 C -4 7 -7 3 -7 -4 Z",
  surprised:  "M -14 -6 C -14 -14 14 -14 14 -6 C 14 6 8 14 0 14 C -8 14 -14 6 -14 -6 Z",
};
const MOUTH_SPEAK_OPEN = "M -19 -7 C -19 -16 -8 -19 0 -19 C 8 -19 19 -16 19 -7 C 19 7 11 16 0 16 C -11 16 -19 7 -19 -7 Z";

// ─── Emotion Poses ───
interface Pose { eyeL: string; eyeR: string; mouth: string; headTilt: number; headNod: number; breathAmp: number; }
const POSES: Record<string, Pose> = {
  idle:       { eyeL: EYE.idle,       eyeR: EYE.idle,       mouth: MOUTH.idle,       headTilt: 0,  headNod: 0,  breathAmp: 1   },
  happy:      { eyeL: EYE.happy,      eyeR: EYE.happy,      mouth: MOUTH.happy,      headTilt: 3,  headNod:-6,  breathAmp: 1.6 },
  sad:        { eyeL: EYE.sad,        eyeR: EYE.sad,        mouth: MOUTH.sad,        headTilt:-4,  headNod: 7,  breathAmp: 0.6 },
  thinking:   { eyeL: EYE.thinkingS,  eyeR: EYE.thinkingL,  mouth: MOUTH.thinking,   headTilt: 7,  headNod:-2,  breathAmp: 0.7 },
  listening:  { eyeL: EYE.listening,  eyeR: EYE.listening,  mouth: MOUTH.listening,  headTilt: 0,  headNod: 0,  breathAmp: 1.0 },
  speaking:   { eyeL: EYE.speaking,   eyeR: EYE.speaking,   mouth: MOUTH.speaking,   headTilt: 1,  headNod:-2,  breathAmp: 1.2 },
  processing: { eyeL: EYE.processing, eyeR: EYE.processing, mouth: MOUTH.processing, headTilt: 0,  headNod: 0,  breathAmp: 0.5 },
  sleeping:   { eyeL: EYE.sleeping,   eyeR: EYE.sleeping,   mouth: MOUTH.sleeping,   headTilt:-4,  headNod:10,  breathAmp: 2.2 },
  surprised:  { eyeL: EYE.surprised,  eyeR: EYE.surprised,  mouth: MOUTH.surprised,  headTilt: 0,  headNod:-8,  breathAmp: 0.3 },
};

// ─── Transition Timing (same as web) ───
interface Timing { anticipation: number; action: number; overshoot: number; settle: number; squashAmt: number; overshootAmt: number; }
const TIMINGS: Record<string, Timing> = {
  bouncy: { anticipation: 0.07, action: 0.24, overshoot: 0.18, settle: 0.35, squashAmt: 0.16, overshootAmt: 0.24 },
  poppy:  { anticipation: 0.05, action: 0.16, overshoot: 0.14, settle: 0.24, squashAmt: 0.2, overshootAmt: 0.28 },
  soft:   { anticipation: 0.09, action: 0.38, overshoot: 0.2, settle: 0.45, squashAmt: 0.1, overshootAmt: 0.15 },
  wobbly: { anticipation: 0.12, action: 0.22, overshoot: 0.28, settle: 0.55, squashAmt: 0.22, overshootAmt: 0.32 },
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

  // ─── Speaking mouth oscillation (v2 — bouncier, more expressive) ───
  useEffect(() => {
    const onSpeak = () => {
      if (!isSpeaking.current || !speakMorph.current) return;
      speakPhase.current += 0.08;
      const t = speakPhase.current;
      const wave = 0.38 + Math.sin(t * 4.6) * 0.36 + Math.sin(t * 9.2) * 0.18 + Math.sin(t * 14) * 0.06 + Math.random() * 0.06;
      const level = clamp(wave, 0, 1);
      const s = sRef.current;
      s.mouth = speakMorph.current.open(level * 0.85);
      s.squashX = 1 + level * 0.035;
      s.squashY = 1 - level * 0.025;
    };
    gsap.ticker.add(onSpeak);
    return () => gsap.ticker.remove(onSpeak);
  }, []);

  // ─── Mouth + squash decay when not speaking ───
  useEffect(() => {
    const onDecay = () => {
      if (isSpeaking.current) return;
      const s = sRef.current;
      const target = targetPose.current;
      if (s.mouth !== target.mouth) {
        try { s.mouth = mkMorph(s.mouth, target.mouth)(0.18); } catch { s.mouth = target.mouth; }
      }
      s.squashX = lerp(s.squashX, 1, 0.08);
      s.squashY = lerp(s.squashY, 1, 0.08);
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

  // v2 ALIVE motion — bigger float, multi-layered
  const floatY = Math.sin(t * 1.4) * 5.5 + Math.sin(t * 2.1) * 2.5 + Math.sin(t * 3.3) * 1.0;
  const floatTilt = Math.sin(t * 0.9) * 1.2 + Math.sin(t * 1.7) * 0.5;
  const breathAmp = targetPose.current.breathAmp ?? 1;
  const speakNod = isSpeaking.current ? Math.sin(t * 5.5) * 2.0 + Math.sin(t * 8.3) * 1.0 + Math.sin(t * 12) * 0.4 : 0;

  const headY = s.headNod + floatY * breathAmp * 0.65 + speakNod;
  const headTilt = s.headTilt + floatTilt;
  const headTx = `translate(${CX}, ${CY + headY}) rotate(${headTilt}) scale(${s.squashX}, ${s.squashY}) translate(${-CX}, ${-CY})`;

  const showBlush = emo === 'happy' || emo === 'speaking' || emo === 'listening';
  const blushOp = emo === 'happy' ? 0.4 : 0.22;

  // Sparkle helpers — bigger, 4 sparkles
  const sp1 = Math.sin(t * 3) * 0.4 + 0.6;
  const sp2 = Math.sin(t * 3 + 2) * 0.4 + 0.6;
  const sp3 = Math.sin(t * 2.5 + 1) * 0.4 + 0.6;
  const sp4 = Math.sin(t * 2.8 + 3) * 0.4 + 0.6;
  const spy1 = Math.sin(t * 2) * 4;
  const spy2 = Math.sin(t * 2.3 + 1) * 4;
  const spy3 = Math.sin(t * 1.8 + 2) * 3;

  // Zzz helpers — slower, bigger, with wobble
  const zCycle = (t * 0.5) % 3;
  const zWobble = Math.sin(t * 1.5) * 2;
  const z1y = -zCycle * 18, z1o = Math.max(0, 1 - zCycle * 0.35);
  const z2y = -(((zCycle + 1) % 3) * 18), z2o = Math.max(0, 1 - ((zCycle + 1) % 3) * 0.35);
  const z3y = -(((zCycle + 2) % 3) * 18), z3o = Math.max(0, 1 - ((zCycle + 2) % 3) * 0.35);

  // Burst helper — with expand pulse
  const burstPulse = 0.7 + Math.sin(t * 6) * 0.3;

  // Dots helpers — scanning motion, bigger
  const scanX = Math.sin(t * 2.5) * 8;
  const dy1 = Math.sin(t * 5) * 6, dy2 = Math.sin(t * 5 + 1.3) * 6, dy3 = Math.sin(t * 5 + 2.6) * 6;
  const dop1 = 0.45 + Math.sin(t * 5) * 0.35, dop2 = 0.45 + Math.sin(t * 5 + 1.3) * 0.35, dop3 = 0.45 + Math.sin(t * 5 + 2.6) * 0.35;

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
          {/* Mouth — filled mochi blob */}
          <G transform={`translate(${A.mouth.x}, ${A.mouth.y})`} opacity={0.95}>
            <Path d={s.mouth} fill="#ffffff" />
          </G>
          {/* Blush — bigger, softer */}
          {showBlush && (
            <G opacity={blushOp}>
              <Ellipse cx={A.eyeL.x - 4} cy={A.eyeL.y + 32} rx={13} ry={7} fill="#ff8ab5" />
              <Ellipse cx={A.eyeR.x + 4} cy={A.eyeR.y + 32} rx={13} ry={7} fill="#ff8ab5" />
            </G>
          )}
          {/* Happy sparkles — 4 sparkles, bigger */}
          {emo === 'happy' && (
            <G opacity={0.75}>
              <G transform={`translate(${A.eyeR.x + 38}, ${A.eyeL.y - 24 + spy1}) scale(${sp1})`}>
                <Line x1={-6} y1={0} x2={6} y2={0} stroke="#ffffff" strokeWidth={2} strokeLinecap="round" />
                <Line x1={0} y1={-6} x2={0} y2={6} stroke="#ffffff" strokeWidth={2} strokeLinecap="round" />
              </G>
              <G transform={`translate(${A.eyeL.x - 36}, ${A.eyeL.y - 14 + spy2}) scale(${sp2})`}>
                <Line x1={-4.5} y1={0} x2={4.5} y2={0} stroke="#ffffff" strokeWidth={1.5} strokeLinecap="round" />
                <Line x1={0} y1={-4.5} x2={0} y2={4.5} stroke="#ffffff" strokeWidth={1.5} strokeLinecap="round" />
              </G>
              <G transform={`translate(${A.eyeR.x + 22}, ${A.mouth.y + 8 + spy3}) scale(${sp3})`}>
                <Line x1={-3.5} y1={0} x2={3.5} y2={0} stroke="#ffffff" strokeWidth={1.2} strokeLinecap="round" />
                <Line x1={0} y1={-3.5} x2={0} y2={3.5} stroke="#ffffff" strokeWidth={1.2} strokeLinecap="round" />
              </G>
              <G transform={`translate(${A.eyeL.x - 20}, ${A.mouth.y - 10}) scale(${sp4})`}>
                <Line x1={-3} y1={0} x2={3} y2={0} stroke="#ffffff" strokeWidth={1} strokeLinecap="round" />
                <Line x1={0} y1={-3} x2={0} y2={3} stroke="#ffffff" strokeWidth={1} strokeLinecap="round" />
              </G>
            </G>
          )}
          {/* Sleeping Zzz — bigger, with wobble */}
          {emo === 'sleeping' && (
            <G transform={`translate(${A.eyeR.x + 36}, ${A.eyeR.y - 16})`}>
              <SvgText x={zWobble} y={z1y} fill="#ffffff" opacity={z1o * 0.75} fontSize={18} fontWeight="bold">Z</SvgText>
              <SvgText x={12 + zWobble} y={z2y - 5} fill="#ffffff" opacity={z2o * 0.55} fontSize={14} fontWeight="bold">z</SvgText>
              <SvgText x={22 + zWobble} y={z3y - 10} fill="#ffffff" opacity={z3o * 0.4} fontSize={11} fontWeight="bold">z</SvgText>
            </G>
          )}
          {/* Surprised burst — more lines, bigger */}
          {emo === 'surprised' && (
            <G opacity={burstPulse * 0.6}>
              <Line x1={A.eyeL.x - 12} y1={A.eyeL.y - 52} x2={A.eyeL.x - 20} y2={A.eyeL.y - 66} stroke="#ffffff" strokeWidth={2} strokeLinecap="round" />
              <Line x1={200} y1={A.eyeL.y - 58} x2={200} y2={A.eyeL.y - 72} stroke="#ffffff" strokeWidth={2} strokeLinecap="round" />
              <Line x1={A.eyeR.x + 12} y1={A.eyeR.y - 52} x2={A.eyeR.x + 20} y2={A.eyeR.y - 66} stroke="#ffffff" strokeWidth={2} strokeLinecap="round" />
              <Line x1={A.eyeL.x + 10} y1={A.eyeL.y - 48} x2={A.eyeL.x + 6} y2={A.eyeL.y - 60} stroke="#ffffff" strokeWidth={1.5} strokeLinecap="round" />
              <Line x1={A.eyeR.x - 10} y1={A.eyeR.y - 48} x2={A.eyeR.x - 6} y2={A.eyeR.y - 60} stroke="#ffffff" strokeWidth={1.5} strokeLinecap="round" />
            </G>
          )}
          {/* Processing dots — scanning motion, bigger */}
          {emo === 'processing' && (
            <G transform={`translate(${A.mouth.x + scanX}, ${A.mouth.y + 24})`}>
              <Circle cx={-16} cy={dy1} r={4} fill="#ffffff" opacity={dop1} />
              <Circle cx={0} cy={dy2} r={4} fill="#ffffff" opacity={dop2} />
              <Circle cx={16} cy={dy3} r={4} fill="#ffffff" opacity={dop3} />
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
