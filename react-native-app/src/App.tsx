import React, { useEffect, useRef, useState } from 'react';
import FaceOverlay from '@/components/FaceOverlay';
import { View, Text, TouchableOpacity, StyleSheet, StatusBar, NativeEventEmitter, NativeModules } from 'react-native';
import { HomeScreen } from '@/screens/HomeScreen';
import { MenuScreen } from '@/screens/MenuScreen';
import { RetailScreen } from '@/screens/RetailScreen';
import { PromoScreen } from '@/screens/PromoScreen';
import { ConfigScreen } from '@/screens/ConfigScreen';
import { NavigationScreen } from '@/screens/NavigationScreen';
import { ChargingScreen } from '@/screens/ChargingScreen';
import { ProductDetailModal } from '@/components/ProductDetailModal';
import { CloudApi } from '@/services/CloudApi';
import { ChargingProvider } from '@/contexts/ChargingContext';
import { useAppStore } from '@/stores/useAppStore';
import { ThemeProvider } from '@/contexts/ThemeContext';

const { RobotSkillModule: SkillModule } = NativeModules;

function ChargingStopButton() {
  const charging = useAppStore((s) => s.charging);
  const [isStopping, setIsStopping] = useState(false);
  const handleStop = async () => {
    if (isStopping || (!charging.isCharging && !charging.isNavigatingToCharger)) {
      return;
    }
    setIsStopping(true);
    try {
      if (SkillModule) await SkillModule.executeAction('com.robbie.action.STOP_CHARGE', '{}');
    } catch (e) { console.error('[ChargingStopButton]', e); }
    finally { setIsStopping(false); }
  };
  return (
    <View style={{
      position: 'absolute',
      bottom: 24,
      left: 0,
      right: 0,
      alignItems: 'center',
      justifyContent: 'center',
      elevation: 99999,
      zIndex: 99999,
    }}>
      <TouchableOpacity
        onPress={handleStop}
        activeOpacity={0.7}
        style={{
          paddingHorizontal: 28,
          paddingVertical: 12,
          backgroundColor: isStopping ? 'rgba(120, 120, 120, 0.85)' : 'rgba(239, 68, 68, 0.85)',
          borderRadius: 24,
          elevation: 99999,
        }}
        disabled={isStopping}
      >
        <Text style={{ color: '#FFF', fontSize: 13, fontWeight: '700' }}>
          {isStopping ? 'Deteniendo carga...' : 'Dejar de cargar'}
        </Text>
      </TouchableOpacity>
    </View>
  );
}

function AppContent() {
  const { currentMode, selectedProduct, setSelectedProduct, navigation, setNavigation, setCurrentMode, productsLoaded, setProducts, charging } = useAppStore();
  const lastChargingUiModeRef = useRef(false);

  useEffect(() => {
    // Cargar productos al inicio de la app
    if (!productsLoaded) {
      console.log('[App] Cargando productos al inicio...');
      CloudApi.getProducts().then((data) => {
        console.log('[App] Productos cargados al inicio:', data.length, 'items');
        setProducts(data);
      }).catch((error) => {
        console.error('[App] Error cargando productos al inicio:', error);
      });
    }

    // Escuchar eventos de navegacion desde el lado nativo
    const eventEmitter = new NativeEventEmitter(NativeModules.DeviceEventEmitter);
    const navigationListener = eventEmitter.addListener('onNavigation', (event) => {
      console.log('[App] Navigation event:', event);
      if (event.isNavigating) {
        setNavigation({
          destination: event.destination,
          isNavigating: true,
        });
        setCurrentMode('navigating');
      } else {
        // Navegacion terminada - ir a retail
        setNavigation(null);
        setCurrentMode('retail');
      }
    });

    // Escuchar eventos de cambio de modo desde comandos de voz
    const modeSwitchListener = eventEmitter.addListener('onModeSwitch', (event) => {
      console.log('[App] Mode switch event:', event);
      let targetMode = event.mode;
      
      // Mapear modos especiales
      if (targetMode === 'exhibition') {
        targetMode = 'promo'; // El modo exhibition se mapea a la pantalla de promociones
      }
      
      console.log('[App] Switching to mode:', targetMode);
      setCurrentMode(targetMode);
    });

    return () => {
      navigationListener.remove();
      modeSwitchListener.remove();
    };
  }, [setNavigation, setCurrentMode]);

  // Refrescar productos cada vez que se entra a retail (por si el panel los actualizó)
  useEffect(() => {
    if (currentMode === 'retail') {
      console.log('[App] Entrando a retail - refrescando productos...');
      CloudApi.refreshProducts().then((data) => {
        if (data.length > 0) {
          console.log('[App] Productos refrescados:', data.length, 'items');
          setProducts(data);
        }
      }).catch((error) => {
        console.warn('[App] Error refrescando productos:', error);
      });
    }
  }, [currentMode]);

  useEffect(() => {
    const shouldShowChargingUi = charging.isCharging || charging.isNavigatingToCharger;
    if (shouldShowChargingUi) {
      lastChargingUiModeRef.current = true;
      if (currentMode !== 'charging') {
        setCurrentMode('charging');
      }
      return;
    }

    if (lastChargingUiModeRef.current && currentMode === 'charging') {
      lastChargingUiModeRef.current = false;
      setCurrentMode('home');
    }
  }, [charging.isCharging, charging.isNavigatingToCharger, currentMode, setCurrentMode]);

  const renderScreen = () => {
    switch (currentMode) {
      case 'charging':
        return <ChargingScreen />;
      case 'navigating':
        return <NavigationScreen />;
      case 'retail':
        return <RetailScreen />;
      case 'promo':
        return <PromoScreen />;
      case 'config':
        return <ConfigScreen />;
      case 'menu':
        return <MenuScreen />;
      case 'home':
      default:
        return <HomeScreen />;
    }
  };

  return (
    <View style={styles.container}>
      <StatusBar barStyle="dark-content" backgroundColor="#FFFFFF" />
      {renderScreen()}
      {selectedProduct && (
        <ProductDetailModal
          product={selectedProduct}
          onClose={() => setSelectedProduct(null)}
        />
      )}
      <FaceOverlay />
      {(charging.isCharging || charging.isNavigatingToCharger || currentMode === 'charging') && <ChargingStopButton />}
    </View>
  );
}

export default function App() {
  return (
    <ThemeProvider>
      <ChargingProvider>
        <AppContent />
      </ChargingProvider>
    </ThemeProvider>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
});
