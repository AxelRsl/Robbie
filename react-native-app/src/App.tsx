import React, { useEffect } from 'react';
import { View, StyleSheet, StatusBar, NativeEventEmitter, NativeModules } from 'react-native';
import { HomeScreen } from '@/screens/HomeScreen';
import { MenuScreen } from '@/screens/MenuScreen';
import { RetailScreen } from '@/screens/RetailScreen';
import { PromoScreen } from '@/screens/PromoScreen';
import { ConfigScreen } from '@/screens/ConfigScreen';
import { NavigationScreen } from '@/screens/NavigationScreen';
import { ProductDetailModal } from '@/components/ProductDetailModal';
import { CloudApi } from '@/services/CloudApi';
import { useAppStore } from '@/stores/useAppStore';
import { ThemeProvider } from '@/contexts/ThemeContext';

function AppContent() {
  const { currentMode, selectedProduct, setSelectedProduct, navigation, setNavigation, setCurrentMode, productsLoaded, setProducts } = useAppStore();

  useEffect(() => {
    // Cargar productos una sola vez al inicio de la app
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

  const renderScreen = () => {
    switch (currentMode) {
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
    </View>
  );
}

export default function App() {
  return (
    <ThemeProvider>
      <AppContent />
    </ThemeProvider>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
});
