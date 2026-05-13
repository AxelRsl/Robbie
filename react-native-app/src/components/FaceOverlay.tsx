import React, { useEffect, useState, useRef, useCallback } from 'react';
import { View, StyleSheet, DeviceEventEmitter } from 'react-native';
import { useCharging } from '@/contexts/ChargingContext';
import { useAppStore } from '@/stores/useAppStore';
import Svg, { Rect, G, Path, Ellipse, Circle, Line, Text as SvgText, Defs, LinearGradient, RadialGradient, Stop, ClipPath, Pattern } from 'react-native-svg';
import gsap from 'gsap';
import { interpolate } from 'flubber';

// ═══════════════════════════════════════════════════════════════════════════════
// KAWAII MASCOT — SVG + Flubber (pixel-perfect match with web RobotFace.tsx)
// ═══════════════════════════════════════════════════════════════════════════════

type MorphFn = (t: number) => string;
const mkMorph = (a: string, b: string): MorphFn => interpolate(a, b, { maxSegmentLength: 6 });
const lerp = (a: number, b: number, t: number) => a + (b - a) * t;
const clamp = (v: number, lo: number, hi: number) => Math.min(hi, Math.max(lo, v));

// ─── Anchors (v4 — compact Dasai Mochi mascot) ───
const A = { eyeL: { x: 145, y: 118 }, eyeR: { x: 255, y: 118 }, mouth: { x: 200, y: 185 } };
const CX = 200, CY = 150;
const LCD_STAGE = { width: 1920, height: 1080, x: 72, y: 44, w: 1776, h: 992, r: 64 };
const FACE_CANVAS = { width: 400, height: 300 };
const FACE_SCALE = Math.min(LCD_STAGE.w / FACE_CANVAS.width, LCD_STAGE.h / FACE_CANVAS.height);
const FACE_OFFSET_X = LCD_STAGE.x + (LCD_STAGE.w - FACE_CANVAS.width * FACE_SCALE) / 2;
const FACE_OFFSET_Y = LCD_STAGE.y + (LCD_STAGE.h - FACE_CANVAS.height * FACE_SCALE) / 2;

// ─── SVG Paths (from paths.ts — identical to web, v4 Dasai Mochi capsules) ───
const EYE: Record<string, string> = {
  idle:       "M -23 -36 C -23 -49 23 -49 23 -36 L 23 36 C 23 49 -23 49 -23 36 Z",
  happy:      "M -28 8 C -28 -18 -13 -24 0 -24 C 13 -24 28 -18 28 8 C 18 4 -18 4 -28 8 Z",
  sad:        "M -20 -32 C -20 -44 20 -44 20 -32 L 20 32 C 20 44 -20 44 -20 32 Z",
  thinkingS:  "M -17 -26 C -17 -38 17 -38 17 -26 L 17 26 C 17 38 -17 38 -17 26 Z",
  thinkingL:  "M -23 -36 C -23 -49 23 -49 23 -36 L 23 36 C 23 49 -23 49 -23 36 Z",
  listening:  "M -24 -39 C -24 -52 24 -52 24 -39 L 24 39 C 24 52 -24 52 -24 39 Z",
  speaking:   "M -23 -33 C -23 -46 23 -46 23 -33 L 23 33 C 23 46 -23 46 -23 33 Z",
  processing: "M -17 -33 C -17 -45 17 -45 17 -33 L 17 33 C 17 45 -17 45 -17 33 Z",
  sleepingL:  "M -27 7 C -22 -13 22 -16 27 3 C 20 -2 -20 2 -27 7 Z",
  sleepingR:  "M -25 4 C -19 -17 21 -13 27 8 C 20 3 -18 -1 -25 4 Z",
  surprised:  "M -30 -40 C -30 -56 30 -56 30 -40 L 30 40 C 30 56 -30 56 -30 40 Z",
};
const EYE_BLINK = "M -23 -3 C -23 -8 23 -8 23 -3 L 23 3 C 23 8 -23 8 -23 3 Z";

const MOUTH: Record<string, string> = {
  idle:       "M -16 -5 C -16 -10 -9 -12 0 -12 C 9 -12 16 -10 16 -5 C 16 3 9 9 0 9 C -9 9 -16 3 -16 -5 Z",
  happy:      "M -24 -5 C -24 -12 -13 -15 0 -15 C 13 -15 24 -12 24 -5 C 24 7 15 17 0 17 C -15 17 -24 7 -24 -5 Z",
  sad:        "M -13 -4 C -13 -9 -7 -10 0 -10 C 7 -10 13 -9 13 -4 C 13 2 7 7 0 7 C -7 7 -13 2 -13 -4 Z",
  thinking:   "M -12 -4 C -12 -9 -5 -10 2 -10 C 8 -9 14 -8 14 -4 C 14 3 8 8 1 8 C -6 8 -12 3 -12 -4 Z",
  listening:  "M -14 -5 C -14 -10 -8 -12 0 -12 C 8 -12 14 -10 14 -5 C 14 3 8 8 0 8 C -8 8 -14 3 -14 -5 Z",
  speaking:   "M -18 -6 C -18 -13 -9 -16 0 -16 C 9 -16 18 -13 18 -6 C 18 5 10 13 0 13 C -10 13 -18 5 -18 -6 Z",
  processing: "M -9 -5 C -9 -10 9 -10 9 -5 C 9 2 5 7 0 7 C -5 7 -9 2 -9 -5 Z",
  sleeping:   "M -12 -6 C -12 -12 12 -12 12 -6 C 12 4 7 10 0 10 C -7 10 -12 4 -12 -6 Z",
  surprised:  "M -16 -8 C -16 -17 16 -17 16 -8 C 16 6 10 16 0 16 C -10 16 -16 6 -16 -8 Z",
};
const MOUTH_SPEAK_OPEN = "M -22 -8 C -22 -18 -10 -22 0 -22 C 10 -22 22 -18 22 -8 C 22 8 13 19 0 19 C -13 19 -22 8 -22 -8 Z";

// ─── Emotion Poses ───
interface Pose { eyeL: string; eyeR: string; mouth: string; headTilt: number; headNod: number; breathAmp: number; }
const POSES: Record<string, Pose> = {
  idle:       { eyeL: EYE.idle,       eyeR: EYE.idle,       mouth: MOUTH.idle,       headTilt: 0,  headNod: 0,  breathAmp: 1.2 },
  happy:      { eyeL: EYE.happy,      eyeR: EYE.happy,      mouth: MOUTH.happy,      headTilt: 4,  headNod:-7,  breathAmp: 1.8 },
  sad:        { eyeL: EYE.sad,        eyeR: EYE.sad,        mouth: MOUTH.sad,        headTilt:-5,  headNod: 8,  breathAmp: 0.6 },
  thinking:   { eyeL: EYE.thinkingS,  eyeR: EYE.thinkingL,  mouth: MOUTH.thinking,   headTilt: 8,  headNod:-2,  breathAmp: 0.7 },
  listening:  { eyeL: EYE.listening,  eyeR: EYE.listening,  mouth: MOUTH.listening,  headTilt: 0,  headNod: 0,  breathAmp: 1.1 },
  speaking:   { eyeL: EYE.speaking,   eyeR: EYE.speaking,   mouth: MOUTH.speaking,   headTilt: 1,  headNod:-2,  breathAmp: 1.3 },
  processing: { eyeL: EYE.processing, eyeR: EYE.processing, mouth: MOUTH.processing, headTilt: 0,  headNod: 0,  breathAmp: 0.5 },
  sleeping:   { eyeL: EYE.sleepingL,  eyeR: EYE.sleepingR,  mouth: MOUTH.sleeping,   headTilt:-7,  headNod:12,  breathAmp: 2.8 },
  surprised:  { eyeL: EYE.surprised,  eyeR: EYE.surprised,  mouth: MOUTH.surprised,  headTilt: 0,  headNod:-9,  breathAmp: 0.3 },
};

// ─── Transition Timing (same as web) ───
interface Timing { anticipation: number; action: number; overshoot: number; settle: number; squashAmt: number; overshootAmt: number; }
const TIMINGS: Record<string, Timing> = {
  bouncy: { anticipation: 0.06, action: 0.22, overshoot: 0.2, settle: 0.4, squashAmt: 0.22, overshootAmt: 0.3 },
  poppy:  { anticipation: 0.04, action: 0.14, overshoot: 0.16, settle: 0.28, squashAmt: 0.26, overshootAmt: 0.34 },
  soft:   { anticipation: 0.08, action: 0.34, overshoot: 0.22, settle: 0.5, squashAmt: 0.14, overshootAmt: 0.2 },
  wobbly: { anticipation: 0.1, action: 0.2, overshoot: 0.3, settle: 0.6, squashAmt: 0.28, overshootAmt: 0.38 },
};
const EMO_TIMING: Record<string, string> = {
  idle:'soft', happy:'poppy', sad:'soft', thinking:'bouncy', listening:'bouncy',
  speaking:'poppy', processing:'bouncy', sleeping:'wobbly', surprised:'wobbly',
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
  const currentMode = useAppStore(s => s.currentMode);
  const charging = useCharging();
  const isChargingMode = currentMode === 'charging'
    || charging.isCharging
    || charging.isNavigatingToCharger
    || charging.status === 'charge_obstacle';
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
  const sleepModeTl = useRef<gsap.core.Timeline | null>(null);
  const lastNonChargingEmotion = useRef('idle');
  const sleepLayer = useRef({ glow: 0, energy: 1 });
  const mountedRef = useRef(false);
  const hideTokenRef = useRef(0);
  const transitionIdRef = useRef(0);
  const persistentEmotionRef = useRef(false);
  const sleepModeActiveRef = useRef(false);
  const lastEmotionEventSignatureRef = useRef('');
  const lastEmotionEventAtRef = useRef(0);

  const cleanupAllAnimations = useCallback((preserveSleepTimeline = false) => {
    if (hideT.current) {
      clearTimeout(hideT.current);
      hideT.current = null;
    }
    tl.current?.kill();
    tl.current = null;
    blinkTw.current?.kill();
    blinkTw.current = null;
    if (!preserveSleepTimeline) {
      sleepModeTl.current?.kill();
      sleepModeTl.current = null;
      sleepModeActiveRef.current = false;
    }
  }, []);

  const primeEmotion = useCallback((name: string, persist = false) => {
    const normalizedName = POSES[name] ? name : 'idle';
    const pose = POSES[normalizedName] ?? POSES.idle;
    targetPose.current = pose;
    emoName.current = normalizedName;
    persistentEmotionRef.current = persist;
    isSpeaking.current = normalizedName === 'speaking';
    sRef.current.eyeL = pose.eyeL;
    sRef.current.eyeR = pose.eyeR;
    sRef.current.mouth = pose.mouth;
    sRef.current.headTilt = pose.headTilt;
    sRef.current.headNod = pose.headNod;
    sRef.current.squashX = 1;
    sRef.current.squashY = 1;
    sRef.current.opacity = 1;
    blinkMorphs.current = { cL: mkMorph(pose.eyeL, EYE_BLINK), cR: mkMorph(pose.eyeR, EYE_BLINK), oL: mkMorph(EYE_BLINK, pose.eyeL), oR: mkMorph(EYE_BLINK, pose.eyeR) };
    speakMorph.current = { open: mkMorph(pose.mouth, MOUTH_SPEAK_OPEN) };
    setVisible(true);
  }, []);

  const scheduleBlink = useCallback(() => {
    if (!mountedRef.current) {
      return;
    }
    blinkTw.current?.kill();
    const delay = 2.0 + Math.random() * 3.0;
    blinkTw.current = gsap.delayedCall(delay, () => {
      if (!mountedRef.current) return;
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

  const transitionToEmotion = useCallback((name: string, persist = false) => {
    const normalizedName = POSES[name] ? name : 'idle';
    if (emoName.current === normalizedName && persistentEmotionRef.current === persist && visible) {
      return;
    }
    const transitionId = ++transitionIdRef.current;
    hideTokenRef.current += 1;
    const fromPose = targetPose.current;
    const toPose = POSES[normalizedName] ?? POSES.idle;
    targetPose.current = toPose;
    emoName.current = normalizedName;
    persistentEmotionRef.current = persist;
    isSpeaking.current = normalizedName === 'speaking';
    setVisible(true);
    cleanupAllAnimations(true);

    morphs.current = { eyeL: mkMorph(fromPose.eyeL, toPose.eyeL), eyeR: mkMorph(fromPose.eyeR, toPose.eyeR), mouth: mkMorph(fromPose.mouth, toPose.mouth) };
    blinkMorphs.current = { cL: mkMorph(toPose.eyeL, EYE_BLINK), cR: mkMorph(toPose.eyeR, EYE_BLINK), oL: mkMorph(EYE_BLINK, toPose.eyeL), oR: mkMorph(EYE_BLINK, toPose.eyeR) };
    speakMorph.current = { open: mkMorph(toPose.mouth, MOUTH_SPEAK_OPEN) };

    const s = sRef.current;
    const mp = { t: 0 };
    const tk = EMO_TIMING[normalizedName] ?? 'bouncy';
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

    if (!persist) {
      const hideToken = ++hideTokenRef.current;
      hideT.current = setTimeout(() => {
        if (!mountedRef.current || hideToken !== hideTokenRef.current || transitionId !== transitionIdRef.current) {
          return;
        }
        blinkTw.current?.kill();
        gsap.to(s, { opacity: 0, duration: 0.4, onComplete: () => {
          if (!mountedRef.current || hideToken !== hideTokenRef.current) {
            return;
          }
          setVisible(false);
        } });
      }, 5000);
    }
  }, [cleanupAllAnimations, scheduleBlink, visible]);

  // ─── Render loop via GSAP ticker ───
  useEffect(() => {
    mountedRef.current = true;
    const onTick = () => {
      if (!mountedRef.current) {
        return;
      }
      timeRef.current = gsap.ticker.time;
      if (visible) tick();
    };
    gsap.ticker.add(onTick);
    return () => {
      mountedRef.current = false;
      gsap.ticker.remove(onTick);
    };
  }, [visible]);

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
      const persist = !!ev._persistSleeping;
      const signature = `${name}|${persist}|${isChargingMode}`;
      const now = Date.now();
      if (signature === lastEmotionEventSignatureRef.current && (now - lastEmotionEventAtRef.current) < 250) {
        return;
      }
      lastEmotionEventSignatureRef.current = signature;
      lastEmotionEventAtRef.current = now;
      if (!isChargingMode && name !== 'sleeping') {
        lastNonChargingEmotion.current = name;
      }
      if (isChargingMode && name !== 'sleeping') {
        return;
      }
      transitionToEmotion(name, persist);
    });

    return () => {
      sub.remove();
      cleanupAllAnimations();
    };
  }, [cleanupAllAnimations, isChargingMode, transitionToEmotion]);

  // ─── Charging mode: activate sleeping and keep alive ───
  useEffect(() => {
    if (isChargingMode) {
      if (!visible || sRef.current.opacity === 0) {
        primeEmotion('sleeping', true);
      }
      transitionToEmotion('sleeping', true);
      if (!sleepModeActiveRef.current) {
        sleepModeTl.current?.kill();
        sleepModeTl.current = gsap.timeline({ repeat: -1, yoyo: true })
          .to(sleepLayer.current, { glow: 0.12, energy: 0.45, duration: 2.6, ease: 'sine.inOut' })
          .to(sleepLayer.current, { glow: 0.04, energy: 0.35, duration: 2.6, ease: 'sine.inOut' });
        sleepModeActiveRef.current = true;
      }
    } else {
      sleepModeTl.current?.kill();
      sleepModeTl.current = null;
      sleepModeActiveRef.current = false;
      gsap.to(sleepLayer.current, { glow: 0, energy: 1, duration: 0.35, ease: 'sine.out' });
      if (emoName.current === 'sleeping') {
        transitionToEmotion(lastNonChargingEmotion.current || 'idle', false);
      }
    }
  }, [isChargingMode, primeEmotion, transitionToEmotion, visible]);

  // ─── RENDER ───
  if (!visible && sRef.current.opacity === 0) return null;

  const s = sRef.current;
  const t = timeRef.current;
  const emo = emoName.current;
  const faceStageTransform = `translate(${FACE_OFFSET_X}, ${FACE_OFFSET_Y}) scale(${FACE_SCALE})`;
  const screenFlashOpacity = isChargingMode ? 0.03 : 0.015;
  const ambientGlowOpacity = isChargingMode ? 0.12 : 0.07;
  const sheenWidth = 420;
  const sheenTravel = LCD_STAGE.x - sheenWidth + ((t * 220) % (LCD_STAGE.w + sheenWidth * 2));

  // v2 ALIVE motion — bigger float, multi-layered
  const energyFactor = isChargingMode ? sleepLayer.current.energy : 1;
  const floatY = (Math.sin(t * 1.3) * 6.5 + Math.sin(t * 2.0) * 3.0 + Math.sin(t * 3.1) * 1.2) * energyFactor;
  const floatTilt = (Math.sin(t * 0.85) * 1.5 + Math.sin(t * 1.6) * 0.6) * (isChargingMode ? 0.35 : 1);
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

  // Zzz helpers — wobblier, drifting, bigger
  const zCycle = (t * 0.45) % 3;
  const zWobble = Math.sin(t * 1.3) * 3.5;
  const zWobble2 = Math.sin(t * 1.7 + 1) * 2.5;
  const zDrift = Math.sin(t * 0.6) * 4;
  const z1y = -zCycle * 20, z1o = Math.max(0, 1 - zCycle * 0.35);
  const z2y = -(((zCycle + 1) % 3) * 20), z2o = Math.max(0, 1 - ((zCycle + 1) % 3) * 0.35);
  const z3y = -(((zCycle + 2) % 3) * 20), z3o = Math.max(0, 1 - ((zCycle + 2) % 3) * 0.35);

  // Sleep bubble helpers — inflate/deflate with breathing
  const bubBreath = Math.sin(t * 0.75);
  const bubScale = Math.max(0.2, 0.45 + bubBreath * 0.5 + Math.sin(t * 1.5) * 0.1);
  const bubWX = Math.sin(t * 1.4) * 3;
  const bubWY = Math.cos(t * 1.1) * 2.5;
  const bubSqX = 1 + Math.sin(t * 2.2) * 0.08;
  const bubSqY = 1 - Math.sin(t * 2.2) * 0.06;
  const bubOp = 0.3 + bubScale * 0.5;

  // Drool helpers — dangling stretchy blob
  const droolWX = Math.sin(t * 2.1) * 2;
  const droolStretch = 1 + Math.sin(t * 1.6) * 0.22 + Math.sin(t * 3.2) * 0.1;
  const droolDrip = Math.abs(Math.sin(t * 0.6)) * 4;
  const droolSway = Math.sin(t * 0.9) * 1.5;
  const droolOp = 0.6 + Math.sin(t * 1.2) * 0.15;

  // Burst helper — with expand pulse
  const burstPulse = 0.7 + Math.sin(t * 6) * 0.3;

  // Dots helpers — scanning motion, bigger
  const scanX = Math.sin(t * 2.5) * 8;
  const dy1 = Math.sin(t * 5) * 6, dy2 = Math.sin(t * 5 + 1.3) * 6, dy3 = Math.sin(t * 5 + 2.6) * 6;
  const dop1 = 0.45 + Math.sin(t * 5) * 0.35, dop2 = 0.45 + Math.sin(t * 5 + 1.3) * 0.35, dop3 = 0.45 + Math.sin(t * 5 + 2.6) * 0.35;

  return (
    <View pointerEvents="none" style={[sty.root, { opacity: s.opacity }]}>
      <Svg viewBox={`0 0 ${LCD_STAGE.width} ${LCD_STAGE.height}`} style={sty.svg}>
        <Defs>
          <LinearGradient id="lcdStageBg" x1="0" y1="0" x2="1" y2="1">
            <Stop offset="0%" stopColor="#020607" />
            <Stop offset="100%" stopColor="#071518" />
          </LinearGradient>
          <LinearGradient id="lcdScreenBg" x1="0" y1="0" x2="0" y2="1">
            <Stop offset="0%" stopColor="#0a1113" />
            <Stop offset="100%" stopColor="#030607" />
          </LinearGradient>
          <LinearGradient id="lcdSheen" x1="0" y1="0" x2="1" y2="0">
            <Stop offset="0%" stopColor="#ffffff" stopOpacity="0" />
            <Stop offset="50%" stopColor="#ffffff" stopOpacity="0.06" />
            <Stop offset="100%" stopColor="#ffffff" stopOpacity="0" />
          </LinearGradient>
          <RadialGradient id="lcdVignette" cx="50%" cy="50%" rx="65%" ry="65%">
            <Stop offset="0%" stopColor="#000000" stopOpacity="0" />
            <Stop offset="100%" stopColor="#000000" stopOpacity="0.58" />
          </RadialGradient>
          <RadialGradient id="lcdAmbientScreen" cx="42%" cy="38%" rx="42%" ry="42%">
            <Stop offset="0%" stopColor="#61f3ff" stopOpacity="0.16" />
            <Stop offset="100%" stopColor="#61f3ff" stopOpacity="0" />
          </RadialGradient>
          <RadialGradient id="rfAmbientGlow" cx="50%" cy="45%" rx="50%" ry="50%">
            <Stop offset="0%" stopColor="#ffffff" stopOpacity={ambientGlowOpacity} />
            <Stop offset="100%" stopColor="#ffffff" stopOpacity="0" />
          </RadialGradient>
          <Pattern id="lcdScanlines" patternUnits="userSpaceOnUse" width="12" height="12">
            <Rect x="0" y="0" width="12" height="1" fill="#ffffff" opacity="0.035" />
            <Rect x="0" y="6" width="12" height="1" fill="#ffffff" opacity="0.02" />
          </Pattern>
          <ClipPath id="lcdScreenClip">
            <Rect x={LCD_STAGE.x} y={LCD_STAGE.y} width={LCD_STAGE.w} height={LCD_STAGE.h} rx={LCD_STAGE.r} />
          </ClipPath>
        </Defs>
        <Rect width={LCD_STAGE.width} height={LCD_STAGE.height} fill="url(#lcdStageBg)" />
        <Ellipse cx={LCD_STAGE.width * 0.42} cy={LCD_STAGE.height * 0.38} rx={540} ry={360} fill="#61f3ff" opacity={0.08} />
        <Ellipse cx={LCD_STAGE.width * 0.5} cy={LCD_STAGE.height * 0.52} rx={760} ry={420} fill="#ffffff" opacity={0.025} />
        <Rect x={LCD_STAGE.x} y={LCD_STAGE.y} width={LCD_STAGE.w} height={LCD_STAGE.h} rx={LCD_STAGE.r} fill="url(#lcdScreenBg)" />
        <Rect x={LCD_STAGE.x} y={LCD_STAGE.y} width={LCD_STAGE.w} height={LCD_STAGE.h} rx={LCD_STAGE.r} fill="url(#lcdAmbientScreen)" />
        <Rect x={LCD_STAGE.x} y={LCD_STAGE.y} width={LCD_STAGE.w} height={LCD_STAGE.h} rx={LCD_STAGE.r} fill="#ffffff" opacity={screenFlashOpacity} />
        <Rect x={LCD_STAGE.x} y={LCD_STAGE.y} width={LCD_STAGE.w} height={LCD_STAGE.h} rx={LCD_STAGE.r} fill="url(#lcdScanlines)" opacity={0.42} />
        <Rect x={LCD_STAGE.x} y={LCD_STAGE.y} width={LCD_STAGE.w} height={LCD_STAGE.h} rx={LCD_STAGE.r} fill="url(#lcdVignette)" />
        <Rect x={LCD_STAGE.x} y={LCD_STAGE.y} width={LCD_STAGE.w} height={LCD_STAGE.h} rx={LCD_STAGE.r} fill="none" stroke="#ffffff" strokeOpacity="0.08" strokeWidth="2" />
        <G clipPath="url(#lcdScreenClip)">
          <Rect x={sheenTravel} y={LCD_STAGE.y} width={sheenWidth} height={LCD_STAGE.h} fill="url(#lcdSheen)" opacity={0.2} transform={`skewX(-18)`} />
          <G transform={faceStageTransform}>
            <Rect width={400} height={300} rx={40} fill="#05090a" />
            <Rect width={400} height={300} rx={40} fill="#ffffff" opacity={screenFlashOpacity} />
            <Rect width={400} height={300} rx={40} fill="none" stroke="#ffffff" strokeOpacity="0.04" strokeWidth="1.5" />
            <Rect width={400} height={300} rx={40} fill="url(#rfAmbientGlow)" />
            <G transform={headTx}>
          {/* Left eye */}
          <G transform={`translate(${A.eyeL.x}, ${A.eyeL.y})`}>
            <Path d={s.eyeL} fill="#ffffff" opacity={0.08} scale={1.26} />
            <Path d={s.eyeL} fill="#ffffff" opacity={0.14} scale={1.13} />
            <Path d={s.eyeL} fill="#ffffff" />
          </G>
          {/* Right eye */}
          <G transform={`translate(${A.eyeR.x}, ${A.eyeR.y})`}>
            <Path d={s.eyeR} fill="#ffffff" opacity={0.08} scale={1.26} />
            <Path d={s.eyeR} fill="#ffffff" opacity={0.14} scale={1.13} />
            <Path d={s.eyeR} fill="#ffffff" />
          </G>
          {/* Mouth — filled mochi blob */}
          <G transform={`translate(${A.mouth.x}, ${A.mouth.y})`} opacity={0.95}>
            <Path d={s.mouth} fill="#ffffff" opacity={0.08} scale={1.22} />
            <Path d={s.mouth} fill="#ffffff" opacity={0.14} scale={1.1} />
            <Path d={s.mouth} fill="#ffffff" />
          </G>
          {/* Blush — below capsule eyes */}
          {showBlush && (
            <G opacity={blushOp}>
              <Ellipse cx={A.eyeL.x - 4} cy={A.eyeL.y + 44} rx={13} ry={7} fill="#ff8ab5" />
              <Ellipse cx={A.eyeR.x + 4} cy={A.eyeR.y + 44} rx={13} ry={7} fill="#ff8ab5" />
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
          {/* Sleeping suite — Zzz, bubble, drool */}
          {emo === 'sleeping' && (
            <>
              {/* Zzz — wobblier, drifting */}
              <G transform={`translate(${A.eyeR.x + 32 + zDrift}, ${A.eyeR.y - 20})`}>
                <SvgText x={zWobble} y={z1y} fill="#ffffff" opacity={z1o * 0.8} fontSize={20} fontWeight="bold">Z</SvgText>
                <SvgText x={14 + zWobble2} y={z2y - 6} fill="#ffffff" opacity={z2o * 0.6} fontSize={15} fontWeight="bold">z</SvgText>
                <SvgText x={24 + zWobble * 0.7} y={z3y - 12} fill="#ffffff" opacity={z3o * 0.4} fontSize={11} fontWeight="bold">z</SvgText>
              </G>
              {/* Sleep bubble — inflate/deflate */}
              <G transform={`translate(${A.mouth.x + 20 + bubWX}, ${A.mouth.y - 8 + bubWY}) scale(${bubScale * bubSqX}, ${bubScale * bubSqY})`}>
                <Circle cx={0} cy={0} r={16} fill="none" stroke="#ffffff" strokeWidth={2.2} opacity={bubOp} />
                <Ellipse cx={-4} cy={-4.5} rx={4} ry={3} fill="#ffffff" opacity={bubOp * 0.45} />
              </G>
              {/* Drool — stretchy dangling blob */}
              <G transform={`translate(${A.mouth.x + 6 + droolWX + droolSway}, ${A.mouth.y + 12 + droolDrip})`} opacity={droolOp}>
                <Ellipse cx={0} cy={-4} rx={3} ry={2} fill="#ffffff" opacity={0.6} />
                <Ellipse cx={0} cy={0} rx={4} ry={6 * droolStretch} fill="#ffffff" opacity={1} />
                <Ellipse cx={0} cy={6 * droolStretch} rx={4.5} ry={4} fill="#ffffff" opacity={0.85} />
              </G>
            </>
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
          </G>
        </G>
      </Svg>
    </View>
  );
};

const sty = StyleSheet.create({
  root: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'transparent',
    zIndex: 9999,
    elevation: 9999,
  },
  svg: {
    width: '100%',
    height: '100%',
  },
});

export default FaceOverlay;
