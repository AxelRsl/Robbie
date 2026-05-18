import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { DeviceEventEmitter, StyleSheet, View } from 'react-native';
import WebView from 'react-native-webview';
import { useCharging } from '@/contexts/ChargingContext';
import { useAppStore } from '@/stores/useAppStore';

type Pose = {
  eyeL: string;
  eyeR: string;
  mouth: string;
  headTilt: number;
  headNod: number;
  breathAmp: number;
  glowIntensity: number;
};

const EYE: Record<string, string> = {
  idle: 'M -23 -36 C -23 -49 23 -49 23 -36 L 23 36 C 23 49 -23 49 -23 36 Z',
  happy: 'M -28 8 C -28 -18 -13 -24 0 -24 C 13 -24 28 -18 28 8 C 18 4 -18 4 -28 8 Z',
  sad: 'M -20 -32 C -20 -44 20 -44 20 -32 L 20 32 C 20 44 -20 44 -20 32 Z',
  thinkingS: 'M -17 -26 C -17 -38 17 -38 17 -26 L 17 26 C 17 38 -17 38 -17 26 Z',
  thinkingL: 'M -23 -36 C -23 -49 23 -49 23 -36 L 23 36 C 23 49 -23 49 -23 36 Z',
  listening: 'M -24 -39 C -24 -52 24 -52 24 -39 L 24 39 C 24 52 -24 52 -24 39 Z',
  speaking: 'M -23 -33 C -23 -46 23 -46 23 -33 L 23 33 C 23 46 -23 46 -23 33 Z',
  processing: 'M -17 -33 C -17 -45 17 -45 17 -33 L 17 33 C 17 45 -17 45 -17 33 Z',
  sleepingL: 'M -27 7 C -22 -13 22 -16 27 3 C 20 -2 -20 2 -27 7 Z',
  sleepingR: 'M -25 4 C -19 -17 21 -13 27 8 C 20 3 -18 -1 -25 4 Z',
  surprised: 'M -30 -40 C -30 -56 30 -56 30 -40 L 30 40 C 30 56 -30 56 -30 40 Z',
};

const MOUTH: Record<string, string> = {
  idle: 'M -16 -5 C -16 -10 -9 -12 0 -12 C 9 -12 16 -10 16 -5 C 16 3 9 9 0 9 C -9 9 -16 3 -16 -5 Z',
  happy: 'M -24 -5 C -24 -12 -13 -15 0 -15 C 13 -15 24 -12 24 -5 C 24 7 15 17 0 17 C -15 17 -24 7 -24 -5 Z',
  sad: 'M -13 -4 C -13 -9 -7 -10 0 -10 C 7 -10 13 -9 13 -4 C 13 2 7 7 0 7 C -7 7 -13 2 -13 -4 Z',
  thinking: 'M -12 -4 C -12 -9 -5 -10 2 -10 C 8 -9 14 -8 14 -4 C 14 3 8 8 1 8 C -6 8 -12 3 -12 -4 Z',
  listening: 'M -14 -5 C -14 -10 -8 -12 0 -12 C 8 -12 14 -10 14 -5 C 14 3 8 8 0 8 C -8 8 -14 3 -14 -5 Z',
  speaking: 'M -18 -6 C -18 -13 -9 -16 0 -16 C 9 -16 18 -13 18 -6 C 18 5 10 13 0 13 C -10 13 -18 5 -18 -6 Z',
  processing: 'M -9 -5 C -9 -10 9 -10 9 -5 C 9 2 5 7 0 7 C -5 7 -9 2 -9 -5 Z',
  sleeping: 'M -12 -6 C -12 -12 12 -12 12 -6 C 12 4 7 10 0 10 C -7 10 -12 4 -12 -6 Z',
  surprised: 'M -16 -8 C -16 -17 16 -17 16 -8 C 16 6 10 16 0 16 C -10 16 -16 6 -16 -8 Z',
};

const POSES: Record<string, Pose> = {
  idle: { eyeL: EYE.idle, eyeR: EYE.idle, mouth: MOUTH.idle, headTilt: 0, headNod: 0, breathAmp: 1.2, glowIntensity: 0.5 },
  happy: { eyeL: EYE.happy, eyeR: EYE.happy, mouth: MOUTH.happy, headTilt: 4, headNod: -7, breathAmp: 1.8, glowIntensity: 0.9 },
  sad: { eyeL: EYE.sad, eyeR: EYE.sad, mouth: MOUTH.sad, headTilt: -5, headNod: 8, breathAmp: 0.6, glowIntensity: 0.2 },
  thinking: { eyeL: EYE.thinkingS, eyeR: EYE.thinkingL, mouth: MOUTH.thinking, headTilt: 8, headNod: -2, breathAmp: 0.7, glowIntensity: 0.55 },
  listening: { eyeL: EYE.listening, eyeR: EYE.listening, mouth: MOUTH.listening, headTilt: 0, headNod: 0, breathAmp: 1.1, glowIntensity: 0.65 },
  speaking: { eyeL: EYE.speaking, eyeR: EYE.speaking, mouth: MOUTH.speaking, headTilt: 1, headNod: -2, breathAmp: 1.3, glowIntensity: 0.7 },
  processing: { eyeL: EYE.processing, eyeR: EYE.processing, mouth: MOUTH.processing, headTilt: 0, headNod: 0, breathAmp: 0.5, glowIntensity: 0.6 },
  sleeping: { eyeL: EYE.sleepingL, eyeR: EYE.sleepingR, mouth: MOUTH.sleeping, headTilt: -7, headNod: 12, breathAmp: 2.8, glowIntensity: 0.15 },
  surprised: { eyeL: EYE.surprised, eyeR: EYE.surprised, mouth: MOUTH.surprised, headTilt: 0, headNod: -9, breathAmp: 0.3, glowIntensity: 1 },
};

const ALIAS: Record<string, string> = {
  neutral: 'idle',
  calm: 'idle',
  sceptic: 'thinking',
  confused: 'thinking',
  suspicious: 'thinking',
  tired: 'sleeping',
  sleepy: 'sleeping',
  broken: 'sad',
  denying: 'sad',
  in_love: 'happy',
  wink: 'happy',
  angry: 'surprised',
  afraid: 'surprised',
  disgusted: 'surprised',
  crazy: 'surprised',
  interested: 'listening',
};

const normalizeEmotion = (name: string) => {
  const key = name.toLowerCase().replace(/\s+/g, '_');
  if (POSES[key]) return key;
  if (ALIAS[key] && POSES[ALIAS[key]]) return ALIAS[key];
  return 'idle';
};

const buildHtml = (emotion: string) => {
  const pose = POSES[emotion] ?? POSES.idle;
  const showBlush = emotion === 'happy' || emotion === 'speaking' || emotion === 'listening';
  const blushOpacity = emotion === 'happy' ? 0.4 : 0.22;
  const ambientGlow = (0.04 + pose.glowIntensity * 0.03).toFixed(3);
  const sparkles = emotion === 'happy'
    ? `
      <g id="sparkles" opacity="0.75">
        <g id="sparkle-1" transform="translate(293, 94)">
          <line x1="-6" y1="0" x2="6" y2="0" stroke="#ffffff" stroke-width="2" stroke-linecap="round" />
          <line x1="0" y1="-6" x2="0" y2="6" stroke="#ffffff" stroke-width="2" stroke-linecap="round" />
        </g>
        <g id="sparkle-2" transform="translate(109, 104)">
          <line x1="-4.5" y1="0" x2="4.5" y2="0" stroke="#ffffff" stroke-width="1.5" stroke-linecap="round" />
          <line x1="0" y1="-4.5" x2="0" y2="4.5" stroke="#ffffff" stroke-width="1.5" stroke-linecap="round" />
        </g>
        <g id="sparkle-3" transform="translate(277, 193)">
          <line x1="-3.5" y1="0" x2="3.5" y2="0" stroke="#ffffff" stroke-width="1.2" stroke-linecap="round" />
          <line x1="0" y1="-3.5" x2="0" y2="3.5" stroke="#ffffff" stroke-width="1.2" stroke-linecap="round" />
        </g>
        <g id="sparkle-4" transform="translate(125, 175)">
          <line x1="-3" y1="0" x2="3" y2="0" stroke="#ffffff" stroke-width="1" stroke-linecap="round" />
          <line x1="0" y1="-3" x2="0" y2="3" stroke="#ffffff" stroke-width="1" stroke-linecap="round" />
        </g>
      </g>`
    : '';
  const sleeping = emotion === 'sleeping'
    ? `
      <g id="sleep-z-group">
        <text id="sleep-z1" x="287" y="98" fill="#ffffff" opacity="0.8" font-size="20" font-weight="700" font-family="sans-serif">Z</text>
        <text id="sleep-z2" x="302" y="76" fill="#ffffff" opacity="0.6" font-size="15" font-weight="700" font-family="sans-serif">z</text>
        <text id="sleep-z3" x="317" y="58" fill="#ffffff" opacity="0.4" font-size="11" font-weight="700" font-family="sans-serif">z</text>
      </g>
      <g id="sleep-bubble" transform="translate(217,174) scale(0.42)">
        <circle cx="0" cy="0" r="16" fill="none" stroke="#ffffff" stroke-width="2.2" opacity="0.58" />
        <ellipse cx="-4" cy="-4.5" rx="4" ry="3" fill="#ffffff" opacity="0.26" />
      </g>
      <g id="sleep-drool" opacity="0.82">
        <ellipse cx="206" cy="193" rx="3" ry="2" fill="#ffffff" opacity="0.6" />
        <ellipse cx="206" cy="199" rx="4" ry="6" fill="#ffffff" />
        <ellipse cx="206" cy="206" rx="4.5" ry="4" fill="#ffffff" opacity="0.85" />
      </g>`
    : '';
  const surprised = emotion === 'surprised'
    ? `
      <g id="surprised-burst" opacity="0.72">
        <line x1="133" y1="66" x2="125" y2="52" stroke="#ffffff" stroke-width="2" stroke-linecap="round" />
        <line x1="200" y1="60" x2="200" y2="46" stroke="#ffffff" stroke-width="2" stroke-linecap="round" />
        <line x1="267" y1="66" x2="275" y2="52" stroke="#ffffff" stroke-width="2" stroke-linecap="round" />
        <line x1="155" y1="70" x2="151" y2="58" stroke="#ffffff" stroke-width="1.5" stroke-linecap="round" />
        <line x1="245" y1="70" x2="249" y2="58" stroke="#ffffff" stroke-width="1.5" stroke-linecap="round" />
      </g>`
    : '';
  const processing = emotion === 'processing'
    ? `
      <g id="processing-dots">
        <circle id="dot-1" cx="184" cy="209" r="4" fill="#ffffff" opacity="0.45" />
        <circle id="dot-2" cx="200" cy="209" r="4" fill="#ffffff" opacity="0.45" />
        <circle id="dot-3" cx="216" cy="209" r="4" fill="#ffffff" opacity="0.45" />
      </g>`
    : '';
  const leftEyeGlow = `
        <g class="rf-glow rf-eye-glow">
          <path d="${pose.eyeL}" fill="#ffffff" opacity="0.1" transform="scale(1.22 1.15)" />
          <path d="${pose.eyeL}" fill="#ffffff" opacity="0.058" transform="scale(1.12 1.08)" />
          <path d="${pose.eyeL}" fill="#ffffff" opacity="0.026" transform="scale(1.05 1.03)" />
        </g>`;
  const rightEyeGlow = `
        <g class="rf-glow rf-eye-glow">
          <path d="${pose.eyeR}" fill="#ffffff" opacity="0.1" transform="scale(1.22 1.15)" />
          <path d="${pose.eyeR}" fill="#ffffff" opacity="0.058" transform="scale(1.12 1.08)" />
          <path d="${pose.eyeR}" fill="#ffffff" opacity="0.026" transform="scale(1.05 1.03)" />
        </g>`;
  const mouthGlow = `
      <g class="rf-glow rf-mouth-glow" transform="translate(200,185)">
        <path d="${pose.mouth}" fill="#ffffff" opacity="0.095" transform="scale(1.2 1.16)" />
        <path d="${pose.mouth}" fill="#ffffff" opacity="0.056" transform="scale(1.11 1.08)" />
        <path d="${pose.mouth}" fill="#ffffff" opacity="0.028" transform="scale(1.04 1.03)" />
      </g>`;

  return `<!DOCTYPE html>
<html>
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no" />
    <style>
      html, body {
        margin: 0;
        width: 100%;
        height: 100%;
        overflow: hidden;
        background: #030607;
      }
      body {
        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      }
      .robot-face-screen {
        position: fixed;
        inset: 0;
        display: grid;
        place-items: center;
        contain: layout paint style;
        background:
          radial-gradient(circle at 50% 42%, rgba(255, 255, 255, 0.045), transparent 34%),
          linear-gradient(180deg, rgba(255, 255, 255, 0.04), transparent 22%),
          #030607;
        box-shadow:
          inset 0 0 0 1px rgba(255, 255, 255, 0.035),
          inset 0 0 42px rgba(0, 0, 0, 0.78);
        overflow: hidden;
      }
      .robot-face-screen::before {
        content: '';
        position: absolute;
        inset: 0;
        pointer-events: none;
        background: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='6' height='6' viewBox='0 0 6 6'%3E%3Crect width='6' height='1' fill='%23ffffff' fill-opacity='0.045'/%3E%3C/svg%3E") repeat;
        background-size: 6px 6px;
        opacity: 0.34;
        z-index: 2;
      }
      .robot-face-sheen {
        position: absolute;
        top: -10%;
        bottom: -10%;
        left: -34vw;
        width: 30vw;
        pointer-events: none;
        background: linear-gradient(90deg, rgba(255, 255, 255, 0), rgba(255, 255, 255, 0.026) 32%, rgba(255, 255, 255, 0.056) 50%, rgba(255, 255, 255, 0.026) 68%, rgba(255, 255, 255, 0));
        opacity: 0.145;
        transform: translate3d(0, 0, 0) skewX(-10deg);
        animation: robot-lcd-sheen 7.8s cubic-bezier(0.35, 0.02, 0.18, 1) infinite;
        z-index: 3;
        will-change: transform;
      }
      .robot-face-svg {
        width: 100vw;
        height: 100vh;
        display: block;
        position: relative;
        z-index: 1;
      }
      .rf-glow path {
        transform-box: fill-box;
        transform-origin: center;
      }
      #head,
      #left-eye-group,
      #right-eye-group,
      #mouth-group,
      #sleep-bubble,
      #sleep-drool {
        will-change: transform, opacity;
      }
      #sleep-z1,
      #sleep-z2,
      #sleep-z3,
      #dot-1,
      #dot-2,
      #dot-3,
      #sparkle-1,
      #sparkle-2,
      #sparkle-3,
      #sparkle-4,
      #surprised-burst {
        transform-box: fill-box;
        transform-origin: center;
      }
      #sleep-z1 { animation: sleep-z-float-1 3.8s linear infinite; }
      #sleep-z2 { animation: sleep-z-float-2 3.8s linear infinite; }
      #sleep-z3 { animation: sleep-z-float-3 3.8s linear infinite; }
      #dot-1 { animation: processing-dot 0.9s ease-in-out infinite; }
      #dot-2 { animation: processing-dot 0.9s ease-in-out 0.15s infinite; }
      #dot-3 { animation: processing-dot 0.9s ease-in-out 0.3s infinite; }
      #sparkle-1 { animation: sparkle-float-1 1.9s ease-in-out infinite; }
      #sparkle-2 { animation: sparkle-float-2 2.05s ease-in-out infinite; }
      #sparkle-3 { animation: sparkle-float-3 2.2s ease-in-out infinite; }
      #sparkle-4 { animation: sparkle-float-4 1.85s ease-in-out infinite; }
      #surprised-burst { animation: burst-pulse 0.42s ease-in-out infinite alternate; }
      @keyframes robot-lcd-sheen {
        0%, 78% { transform: translate3d(0, 0, 0) skewX(-10deg); }
        100% { transform: translate3d(154vw, 0, 0) skewX(-10deg); }
      }
      @keyframes sleep-z-float-1 {
        0% { transform: translate(0px, 0px); opacity: 0.8; }
        100% { transform: translate(7px, -24px); opacity: 0; }
      }
      @keyframes sleep-z-float-2 {
        0% { transform: translate(0px, 0px); opacity: 0.6; }
        100% { transform: translate(5px, -22px); opacity: 0; }
      }
      @keyframes sleep-z-float-3 {
        0% { transform: translate(0px, 0px); opacity: 0.4; }
        100% { transform: translate(4px, -19px); opacity: 0; }
      }
      @keyframes processing-dot {
        0%, 100% { transform: translateY(0px); opacity: 0.34; }
        50% { transform: translateY(-6px); opacity: 0.82; }
      }
      @keyframes sparkle-float-1 {
        0%, 100% { transform: translateY(0px) scale(0.68); opacity: 0.56; }
        50% { transform: translateY(-4px) scale(1); opacity: 0.92; }
      }
      @keyframes sparkle-float-2 {
        0%, 100% { transform: translateY(0px) scale(0.62); opacity: 0.5; }
        50% { transform: translateY(-4px) scale(0.96); opacity: 0.85; }
      }
      @keyframes sparkle-float-3 {
        0%, 100% { transform: translateY(0px) scale(0.58); opacity: 0.46; }
        50% { transform: translateY(-3px) scale(0.9); opacity: 0.8; }
      }
      @keyframes sparkle-float-4 {
        0%, 100% { transform: scale(0.6); opacity: 0.44; }
        50% { transform: scale(0.9); opacity: 0.74; }
      }
      @keyframes burst-pulse {
        0% { opacity: 0.32; }
        100% { opacity: 0.56; }
      }
    </style>
  </head>
  <body data-emotion="${emotion}">
    <div class="robot-face-screen">
      <div class="robot-face-sheen"></div>
      <svg viewBox="0 0 400 300" class="robot-face-svg" preserveAspectRatio="xMidYMid slice">
        <defs>
          <radialGradient id="rf-ambientGlow" cx="50%" cy="45%" r="50%">
            <stop offset="0%" stop-color="#ffffff" stop-opacity="${ambientGlow}" />
            <stop offset="100%" stop-color="#ffffff" stop-opacity="0" />
          </radialGradient>
        </defs>
        <rect width="400" height="300" rx="40" fill="#05090a" />
        <rect width="400" height="300" rx="40" fill="#ffffff" opacity="0.012" />
        <rect width="400" height="300" rx="40" fill="none" stroke="#ffffff" stroke-opacity="0.04" stroke-width="1.5" />
        <ellipse cx="200" cy="145" rx="160" ry="110" fill="url(#rf-ambientGlow)" />
        <g id="head">
          ${mouthGlow}
          <g id="left-eye-group" transform="translate(145,118)">
            ${leftEyeGlow}
            <path d="${pose.eyeL}" fill="#ffffff" />
          </g>
          <g id="right-eye-group" transform="translate(255,118)">
            ${rightEyeGlow}
            <path d="${pose.eyeR}" fill="#ffffff" />
          </g>
          <g id="mouth-group" transform="translate(200,185)">
            <path d="${pose.mouth}" fill="#ffffff" opacity="0.95" />
          </g>
          ${showBlush ? `<g opacity="${blushOpacity}"><ellipse cx="141" cy="162" rx="13" ry="7" fill="#ff8ab5" /><ellipse cx="259" cy="162" rx="13" ry="7" fill="#ff8ab5" /></g>` : ''}
          ${sparkles}
          ${sleeping}
          ${surprised}
          ${processing}
        </g>
      </svg>
    </div>
    <script>
      const emotion = ${JSON.stringify(emotion)};
      const baseHeadNod = ${pose.headNod};
      const baseHeadTilt = ${pose.headTilt};
      const breathAmp = ${pose.breathAmp};
      const sleeping = emotion === 'sleeping';
      const head = document.getElementById('head');
      const sleepBubble = document.getElementById('sleep-bubble');
      const sleepDrool = document.getElementById('sleep-drool');
      const mouth = document.getElementById('mouth-group');
      const leftEye = document.getElementById('left-eye-group');
      const rightEye = document.getElementById('right-eye-group');
      const targetFps = sleeping ? 24 : (emotion === 'idle' ? 30 : 36);
      const frameInterval = 1000 / targetFps;
      const baseLeftEyeX = 145;
      const baseRightEyeX = 255;
      const baseEyeY = 118;
      const idleTargets = [-4.6, -3.2, 0, 0, 0, 3.2, 4.6];
      let lastFrameTs = 0;
      let rafId = 0;
      let lastHeadTransform = '';
      let lastLeftEyeTransform = '';
      let lastRightEyeTransform = '';
      let lastBubbleTransform = '';
      let lastDroolTransform = '';
      let lastDroolOpacity = '';
      let lastMouthTransform = '';
      let idleEyeCurrentX = 0;
      let idleEyeVelocityX = 0;
      let idleEyeTargetX = 0;
      let nextIdleRetargetAt = 900 + Math.random() * 1000;
      let idleEyePhase = Math.PI * 0.37;
      const formatNumber = (value) => (Math.round(value * 100) / 100).toString();
      const setAttributeIfChanged = (node, name, value, lastValue) => {
        if (!node || value === lastValue) {
          return lastValue;
        }
        node.setAttribute(name, value);
        return value;
      };
      const retargetIdleEyes = (now) => {
        const nextTarget = idleTargets[Math.floor(Math.random() * idleTargets.length)];
        idleEyeTargetX = nextTarget;
        idleEyePhase += 0.24 + Math.random() * 0.28;
        nextIdleRetargetAt = now + 1500 + Math.random() * 2600;
      };
      function animate(ts) {
        if (lastFrameTs && (ts - lastFrameTs) < frameInterval) {
          rafId = requestAnimationFrame(animate);
          return;
        }
        lastFrameTs = ts;
        const t = ts / 1000;
        const floatY = Math.sin(t * 1.3) * 6.5 + Math.sin(t * 2.0) * 3.0 + Math.sin(t * 3.1) * 1.2;
        const floatTilt = (Math.sin(t * 0.85) * 1.5 + Math.sin(t * 1.6) * 0.6) * (sleeping ? 0.35 : 1);
        const speakNod = emotion === 'speaking' ? Math.sin(t * 5.5) * 2.0 + Math.sin(t * 8.3) * 1.0 + Math.sin(t * 12) * 0.4 : 0;
        const headY = baseHeadNod + floatY * breathAmp * 0.65 + speakNod;
        const headTilt = baseHeadTilt + floatTilt;
        const headTransform = 'translate(200,' + formatNumber(150 + headY) + ') rotate(' + formatNumber(headTilt) + ') translate(-200,-150)';
        lastHeadTransform = setAttributeIfChanged(head, 'transform', headTransform, lastHeadTransform);
        if (emotion === 'idle') {
          if (ts >= nextIdleRetargetAt) {
            retargetIdleEyes(ts);
          }
          const springForce = (idleEyeTargetX - idleEyeCurrentX) * 0.11;
          idleEyeVelocityX = (idleEyeVelocityX + springForce) * 0.78;
          idleEyeCurrentX += idleEyeVelocityX;
          const idleMicroDrift = Math.sin(t * 0.33 + idleEyePhase) * 0.42 + Math.sin(t * 0.76 + idleEyePhase * 0.7) * 0.16;
          const eyeX = Math.max(-5.4, Math.min(5.4, idleMicroDrift + idleEyeCurrentX));
          const leftEyeTransform = 'translate(' + formatNumber(baseLeftEyeX + eyeX) + ',' + formatNumber(baseEyeY) + ')';
          const rightEyeTransform = 'translate(' + formatNumber(baseRightEyeX + eyeX) + ',' + formatNumber(baseEyeY) + ')';
          lastLeftEyeTransform = setAttributeIfChanged(leftEye, 'transform', leftEyeTransform, lastLeftEyeTransform);
          lastRightEyeTransform = setAttributeIfChanged(rightEye, 'transform', rightEyeTransform, lastRightEyeTransform);
        } else {
          const returnForce = (0 - idleEyeCurrentX) * 0.14;
          idleEyeVelocityX = (idleEyeVelocityX + returnForce) * 0.68;
          idleEyeCurrentX += idleEyeVelocityX;
          const leftEyeTransform = 'translate(' + formatNumber(baseLeftEyeX + idleEyeCurrentX) + ',' + formatNumber(baseEyeY) + ')';
          const rightEyeTransform = 'translate(' + formatNumber(baseRightEyeX + idleEyeCurrentX) + ',' + formatNumber(baseEyeY) + ')';
          lastLeftEyeTransform = setAttributeIfChanged(leftEye, 'transform', leftEyeTransform, lastLeftEyeTransform);
          lastRightEyeTransform = setAttributeIfChanged(rightEye, 'transform', rightEyeTransform, lastRightEyeTransform);
        }
        if (sleeping && sleepBubble && sleepDrool) {
          const bubbleScale = 0.34 + Math.max(0, Math.sin(t * 0.72)) * 0.18;
          const bubbleX = 217 + Math.sin(t * 0.85) * 1.1;
          const bubbleY = 174 + Math.cos(t * 0.72) * 0.9;
          const bubbleSX = bubbleScale * (1 + Math.sin(t * 1.8) * 0.03);
          const bubbleSY = bubbleScale * (1 - Math.sin(t * 1.8) * 0.02);
          const bubbleTransform = 'translate(' + formatNumber(bubbleX) + ',' + formatNumber(bubbleY) + ') scale(' + formatNumber(bubbleSX) + ',' + formatNumber(bubbleSY) + ')';
          lastBubbleTransform = setAttributeIfChanged(sleepBubble, 'transform', bubbleTransform, lastBubbleTransform);
          const droolX = 206 + Math.sin(t * 2.1) * 2 + Math.sin(t * 0.9) * 1.5;
          const droolY = 199 + Math.abs(Math.sin(t * 0.6)) * 4;
          const droolTransform = 'translate(' + formatNumber(droolX - 206) + ',' + formatNumber(droolY - 199) + ')';
          lastDroolTransform = setAttributeIfChanged(sleepDrool, 'transform', droolTransform, lastDroolTransform);
          lastDroolOpacity = setAttributeIfChanged(sleepDrool, 'opacity', formatNumber(0.6 + Math.sin(t * 1.2) * 0.15), lastDroolOpacity);
        }
        if (emotion === 'speaking' && mouth) {
          const mouthTransform = 'translate(200,' + formatNumber(185 + Math.sin(t * 4.6) * 2.8 + Math.sin(t * 7.1) * 0.8) + ')';
          lastMouthTransform = setAttributeIfChanged(mouth, 'transform', mouthTransform, lastMouthTransform);
        }
        rafId = requestAnimationFrame(animate);
      }
      rafId = requestAnimationFrame(animate);
      window.addEventListener('beforeunload', () => cancelAnimationFrame(rafId));
    </script>
  </body>
</html>`;
};

const IDLE_FACE_RETURN_DELAY_MS = 10 * 60 * 1000;

export default function FaceOverlayWebView() {
  const currentMode = useAppStore(s => s.currentMode);
  const agent = useAppStore(s => s.agent);
  const charging = useCharging();
  const shouldShowIdleFace = currentMode === 'home';
  const isChargingMode = currentMode === 'charging'
    || charging.isCharging
    || charging.isNavigatingToCharger
    || charging.status === 'charge_obstacle';
  const shouldHideForPresence = agent.personVisible;
  const shouldHideForAgentState = agent.status === 'thinking' || agent.status === 'processing';
  const shouldForceHideFace = !isChargingMode && (shouldHideForPresence || shouldHideForAgentState);
  const [visible, setVisible] = useState(shouldShowIdleFace);
  const [emotion, setEmotion] = useState('idle');
  const [idleCooldownUntil, setIdleCooldownUntil] = useState(0);
  const [isTemporaryEmotionActive, setIsTemporaryEmotionActive] = useState(false);
  const hideTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const idleResumeTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastSignatureRef = useRef('');
  const lastSignatureAtRef = useRef(0);
  const lastAgentEmotionRef = useRef<string | null>(null);
  const forceHideWasActiveRef = useRef(false);

  const clearHideTimeout = useCallback(() => {
    if (hideTimeoutRef.current) {
      clearTimeout(hideTimeoutRef.current);
      hideTimeoutRef.current = null;
    }
  }, []);

  const clearIdleResumeTimeout = useCallback(() => {
    if (idleResumeTimeoutRef.current) {
      clearTimeout(idleResumeTimeoutRef.current);
      idleResumeTimeoutRef.current = null;
    }
  }, []);

  const startIdleCooldown = useCallback(() => {
    clearIdleResumeTimeout();
    setIdleCooldownUntil(Date.now() + IDLE_FACE_RETURN_DELAY_MS);
  }, [clearIdleResumeTimeout]);

  useEffect(() => {
    if (isChargingMode) {
      clearHideTimeout();
      clearIdleResumeTimeout();
      setIsTemporaryEmotionActive(false);
      lastAgentEmotionRef.current = null;
      forceHideWasActiveRef.current = false;
      setEmotion('sleeping');
      setVisible(true);
      return;
    }

    if (emotion === 'sleeping') {
      setEmotion('idle');
    }
  }, [clearHideTimeout, clearIdleResumeTimeout, emotion, isChargingMode]);

  useEffect(() => {
    const sub = DeviceEventEmitter.addListener('onEmotionAction', (event) => {
      const nextEmotion = normalizeEmotion(event?.emotion || 'idle');
      const persist = !!event?._persistSleeping;
      const signature = `${nextEmotion}|${persist}|${isChargingMode}`;
      const now = Date.now();

      if (signature === lastSignatureRef.current && now - lastSignatureAtRef.current < 250) {
        return;
      }

      lastSignatureRef.current = signature;
      lastSignatureAtRef.current = now;

      if (isChargingMode && nextEmotion !== 'sleeping') {
        return;
      }

      clearHideTimeout();
      clearIdleResumeTimeout();

      if (shouldForceHideFace) {
        setIsTemporaryEmotionActive(false);
        setEmotion('idle');
        setVisible(false);
        return;
      }

      setEmotion(nextEmotion);
      setVisible(true);

      if (!persist && !isChargingMode && nextEmotion !== 'idle') {
        setIsTemporaryEmotionActive(true);
        hideTimeoutRef.current = setTimeout(() => {
          setIsTemporaryEmotionActive(false);
          setEmotion('idle');
          setVisible(false);
          startIdleCooldown();
        }, 5000);
      } else {
        setIsTemporaryEmotionActive(false);
      }
    });

    return () => {
      sub.remove();
      clearHideTimeout();
      clearIdleResumeTimeout();
    };
  }, [clearHideTimeout, clearIdleResumeTimeout, isChargingMode, shouldForceHideFace, startIdleCooldown]);

  useEffect(() => {
    if (isChargingMode) {
      return;
    }

    if (shouldForceHideFace) {
      if (!forceHideWasActiveRef.current) {
        startIdleCooldown();
        forceHideWasActiveRef.current = true;
      }
      clearHideTimeout();
      clearIdleResumeTimeout();
      setIsTemporaryEmotionActive(false);
      setEmotion('idle');
      setVisible(false);
      lastAgentEmotionRef.current = null;
      return;
    }

    forceHideWasActiveRef.current = false;

    if (isTemporaryEmotionActive) {
      return;
    }

    let nextEmotion: string | null = null;
    if (agent.status === 'listening' && agent.gateOpen && !agent.personVisible) {
      nextEmotion = 'listening';
    } else if (agent.status === 'reset_status') {
      nextEmotion = null;
    }

    clearIdleResumeTimeout();

    if (nextEmotion) {
      lastAgentEmotionRef.current = nextEmotion;
      setEmotion(nextEmotion);
      setVisible(true);
      return;
    }

    if (lastAgentEmotionRef.current !== null) {
      lastAgentEmotionRef.current = null;
      setEmotion('idle');
      setVisible(false);
      startIdleCooldown();
      return;
    }

    setEmotion('idle');

    if (!shouldShowIdleFace) {
      setVisible(false);
      return;
    }

    const remainingCooldownMs = idleCooldownUntil - Date.now();
    if (remainingCooldownMs > 0) {
      setVisible(false);
      idleResumeTimeoutRef.current = setTimeout(() => {
        setEmotion('idle');
        setVisible(true);
      }, remainingCooldownMs);
      return;
    }

    setVisible(true);
  }, [agent.gateOpen, agent.personVisible, agent.status, clearHideTimeout, clearIdleResumeTimeout, idleCooldownUntil, isChargingMode, isTemporaryEmotionActive, shouldForceHideFace, shouldShowIdleFace, startIdleCooldown]);

  const html = useMemo(() => buildHtml(emotion), [emotion]);

  if (!visible) {
    return null;
  }

  return (
    <View pointerEvents="none" style={styles.root}>
      <WebView
        originWhitelist={["*"]}
        pointerEvents="none"
        source={{ html }}
        style={styles.webview}
        scrollEnabled={false}
        showsHorizontalScrollIndicator={false}
        showsVerticalScrollIndicator={false}
        overScrollMode="never"
        bounces={false}
        javaScriptEnabled
        domStorageEnabled
        androidLayerType="hardware"
        setSupportMultipleWindows={false}
      />
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    ...StyleSheet.absoluteFillObject,
    zIndex: 9999,
    elevation: 9999,
  },
  webview: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'transparent',
  },
});
