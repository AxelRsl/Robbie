import React, { useEffect } from 'react';
import { View, StyleSheet, StatusBar, NativeEventEmitter, NativeModules } from 'react-native';
import { MenuScreen } from '@/screens/MenuScreen';
import { RetailScreen } from '@/screens/RetailScreen';
import { PromoScreen } from '@/screens/PromoScreen';
import { ConfigScreen } from '@/screens/ConfigScreen';
import { NavigationScreen } from '@/screens/NavigationScreen';
import { ProductDetailModal } from '@/components/ProductDetailModal';
import { CloudApi } from '@/services/CloudApi';
import { useAppStore } from '@/stores/useAppStore';

export default function App() {
  const { currentMode, selectedProduct, setSelectedProduct, navigation, setNavigation, setCurrentMode } = useAppStore();

  useEffect(() => {
    // CloudApi.initialize(); // Comentado: credenciales invalidas, usar modo local

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

    return () => {
      navigationListener.remove();
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
      default:
        return <MenuScreen />;
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

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
});
