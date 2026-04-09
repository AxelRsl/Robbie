import React, { useEffect } from 'react';
import { View, StyleSheet, StatusBar } from 'react-native';
import { MenuScreen } from '@/screens/MenuScreen';
import { RetailScreen } from '@/screens/RetailScreen';
import { PromoScreen } from '@/screens/PromoScreen';
import { ConfigScreen } from '@/screens/ConfigScreen';
import { ProductDetailModal } from '@/components/ProductDetailModal';
import { CloudApi } from '@/services/CloudApi';
import { useAppStore } from '@/stores/useAppStore';

export default function App() {
  const { currentMode, selectedProduct, setSelectedProduct } = useAppStore();

  useEffect(() => {
    // CloudApi.initialize(); // Comentado: credenciales invalidas, usar modo local
  }, []);

  const renderScreen = () => {
    switch (currentMode) {
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
