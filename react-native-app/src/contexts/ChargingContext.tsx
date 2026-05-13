import React, { createContext, useContext, useEffect, useMemo, useRef, useCallback } from 'react';
import { AppState, DeviceEventEmitter } from 'react-native';
import { RobotBridge } from '@/services/RobotBridge';
import { useAppStore, type ChargingUiState } from '@/stores/useAppStore';

const ChargingContext = createContext<ChargingUiState | null>(null);

export const ChargingProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const charging = useAppStore((s) => s.charging);
  const setChargingState = useAppStore((s) => s.setChargingState);
  const mountedRef = useRef(false);
  const hydrationRequestIdRef = useRef(0);
  const lastEventSignatureRef = useRef('');

  const applyChargingEvent = useCallback((event: Partial<ChargingUiState>) => {
    const nextState: Partial<ChargingUiState> = {
      status: event.status || '',
      message: event.message || '',
      batteryLevel: typeof event.batteryLevel === 'number' ? event.batteryLevel : null,
      isCharging: !!event.isCharging,
      isNavigatingToCharger: !!event.isNavigatingToCharger,
      robotApiConnected: event.robotApiConnected !== false,
      autoTriggered: !!event.autoTriggered,
    };

    const signature = [
      nextState.status,
      nextState.message,
      nextState.batteryLevel,
      nextState.isCharging,
      nextState.isNavigatingToCharger,
      nextState.robotApiConnected,
      nextState.autoTriggered,
    ].join('|');

    if (signature === lastEventSignatureRef.current) {
      return;
    }

    lastEventSignatureRef.current = signature;
    setChargingState(nextState);
  }, [setChargingState]);

  const hydrateChargingState = useCallback(async () => {
    const requestId = ++hydrationRequestIdRef.current;
    try {
      const nativeState = await RobotBridge.getBatteryInfo();
      if (!mountedRef.current || requestId !== hydrationRequestIdRef.current || !nativeState) {
        return;
      }
      applyChargingEvent({
        status: typeof nativeState.status === 'string' ? nativeState.status : '',
        message: typeof nativeState.message === 'string' ? nativeState.message : '',
        batteryLevel: typeof nativeState.level === 'number' ? nativeState.level : null,
        isCharging: !!nativeState.isCharging,
        isNavigatingToCharger: !!nativeState.isNavigatingToCharger,
        robotApiConnected: nativeState.robotApiConnected !== false,
        autoTriggered: !!nativeState.autoTriggered,
      });
    } catch (error) {
      console.warn('[ChargingProvider] Failed to hydrate charging state', error);
    }
  }, [applyChargingEvent]);

  useEffect(() => {
    mountedRef.current = true;
    hydrateChargingState();

    const chargingListener = DeviceEventEmitter.addListener('onChargingStatus', (event) => {
      applyChargingEvent(event || {});
    });

    const appStateListener = AppState.addEventListener('change', (nextState) => {
      if (nextState === 'active') {
        hydrateChargingState();
      }
    });

    return () => {
      mountedRef.current = false;
      hydrationRequestIdRef.current += 1;
      chargingListener.remove();
      appStateListener.remove();
    };
  }, [applyChargingEvent, hydrateChargingState]);

  const value = useMemo(() => charging, [charging]);

  return (
    <ChargingContext.Provider value={value}>
      {children}
    </ChargingContext.Provider>
  );
};

export function useCharging() {
  const context = useContext(ChargingContext);
  if (!context) {
    return useAppStore.getState().charging;
  }
  return context;
}
