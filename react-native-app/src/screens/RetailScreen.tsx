import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  View,
  FlatList,
  StyleSheet,
  Text,
  TouchableOpacity,
  NativeModules,
} from 'react-native';
import { ProductCard } from '@/components/ProductCard';
import { SearchBar } from '@/components/SearchBar';
import { CloudApi } from '@/services/CloudApi';
import { RobotBridge } from '@/services/RobotBridge';
import { useAppStore } from '@/stores/useAppStore';
import type { Product } from '@/types';
import { LedHelper } from '@/utils/LedHelper';
import { useTheme } from '@/contexts/ThemeContext';
import { createStyles, GlobalStyles } from '@/theme/styles';
import { GlassPanel } from '@/components/ui/GlassPanel';
import { Icon } from '@/components/ui/Icon';
import { Button } from '@/components/ui/Button';

const { ProductSearchModule } = NativeModules;

export const RetailScreen: React.FC = () => {
  const [isSearching, setIsSearching] = useState(false);
  const { theme } = useTheme();
  const styles = createStyles(theme);
  const mountTimeRef = useRef(Date.now());
  
  const { 
    retailTemplate, 
    setRetailTemplate, 
    setSelectedProduct, 
    setCurrentMode, 
    uiConfig,
    searchQuery,
    searchRecommendation,
    setSearchRecommendation,
    setSearchQuery,
    searchResults: storeSearchResults,
    setSearchResults: setStoreSearchResults,
    products,
    productsLoaded,
  } = useAppStore();

  useEffect(() => {
    console.log(`[RetailScreen] mounted at ${mountTimeRef.current}`);
  }, []);

  const hasActiveSearch = storeSearchResults.length > 0 || !!searchRecommendation || !!searchQuery;

  const searchCategory = useMemo(() => {
    if (storeSearchResults.length === 0) {
      return '';
    }
    const categories = [...new Set(storeSearchResults.map((p: Product) => p.category).filter(Boolean))];
    return categories.join(', ') || '';
  }, [storeSearchResults]);

  const currentSearchQuery = searchQuery || '';

  const displayProducts = useMemo(
    () => (hasActiveSearch ? storeSearchResults : products),
    [hasActiveSearch, products, storeSearchResults]
  );

  useEffect(() => {
    console.log(`[RetailScreen] data ready after ${Date.now() - mountTimeRef.current}ms activeSearch=${hasActiveSearch} count=${displayProducts.length}`);
  }, [displayProducts.length, hasActiveSearch]);

  useEffect(() => {
    // Establecer color Ikalp sólido al cargar la pantalla
    LedHelper.setIkalpPrimary().catch(error => {
      console.warn('[RetailScreen] No se pudo establecer color Ikalp:', error);
    });

    // Escuchar eventos de cambio de modo automático
    const modeSwitchListener = require('react-native').DeviceEventEmitter.addListener('onModeSwitch', (event: any) => {
      console.log('[RetailScreen] Mode switch event from LLM:', event);
      if (event.mode === 'retail' && event.autoSwitch) {
        console.log('[RetailScreen] Auto-switching to retail mode');
        setCurrentMode('retail');
      }
    });

    return () => {
      modeSwitchListener.remove();
    };
  }, []);

  const handleSearch = async (query: string) => {
    console.log('[RetailScreen] Buscando productos con query:', query);
    setIsSearching(true);
    try {
      // Usar búsqueda local en BD con ProductSearchModule
      if (ProductSearchModule) {
        const response = await ProductSearchModule.searchProducts(query);
        const results = response.products || [];
        console.log('[RetailScreen] Resultados de busqueda local:', results.length);
        setStoreSearchResults(results);
        
        await RobotBridge.say(`Encontré ${results.length} productos relacionados con ${query}`);
      } else {
        // Fallback a CloudApi si el módulo no está disponible
        const results = await CloudApi.searchProducts(query);
        console.log('[RetailScreen] Resultados de busqueda CloudApi:', results.length);
        setStoreSearchResults(results);
        
        await RobotBridge.say(`Encontré ${results.length} productos relacionados con ${query}`);
      }
    } catch (error) {
      console.error('[RetailScreen] Error searching:', error);
    } finally {
      setIsSearching(false);
    }
  };

  const handleVoiceSearch = async () => {
    try {
      await RobotBridge.say('¿Qué producto estás buscando?');
      
      setTimeout(async () => {
        const mockQuery = 'laptop';
        await handleSearch(mockQuery);
      }, 2000);
    } catch (error) {
      console.error('Error with voice search:', error);
    }
  };

  const handleProductPress = async (product: Product) => {
    setSelectedProduct(product);
    await RobotBridge.say(`${product.name}. Precio: ${product.price} pesos. ${product.description}`);
  };

  const toggleTemplate = () => {
    setRetailTemplate(retailTemplate === 'grid' ? 'list' : 'grid');
  };

  const handleClearSearch = useCallback(() => {
    setStoreSearchResults([]);
    setSearchRecommendation('');
    setSearchQuery('');
  }, [setSearchRecommendation, setSearchQuery, setStoreSearchResults]);

  const handleRenderProduct = useCallback(({ item }: { item: Product }) => (
    <ProductCard
      product={item}
      onPress={handleProductPress}
      variant={retailTemplate}
    />
  ), [handleProductPress, retailTemplate]);

  if (!productsLoaded) {
    return (
      <View style={[styles.container, GlobalStyles.center]}>
        <Icon name="loading" size="xl" color={theme.colors.primary} />
        <Text style={[styles.body, { marginTop: 16 }]}>Cargando productos...</Text>
      </View>
    );
  }

  return (
    <View style={[styles.container, { backgroundColor: theme.colors.background }]}>
      {/* Header minimalista */}
      <View style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, paddingVertical: 10 }}>
        <TouchableOpacity onPress={() => setCurrentMode('menu')} activeOpacity={0.7}>
          <Icon name="chevronLeft" size="lg" color={theme.colors.primary} />
        </TouchableOpacity>
        <View style={{ flex: 1 }} />
      </View>

      {uiConfig.showSearchBar && (
        <SearchBar 
          onSearch={handleSearch} 
          onVoiceSearch={uiConfig.showMicButton ? handleVoiceSearch : undefined}
          placeholder="Buscar productos con voz o texto..."
        />
      )}

      {/* Info de busqueda horizontal y minimalista */}
      {(currentSearchQuery || searchCategory || isSearching) && (
        <View style={{ flexDirection: 'row', alignItems: 'center', paddingHorizontal: 16, paddingVertical: 6 }}>
          {isSearching && <Icon name="loading" size="sm" color={theme.colors.primary} />}
          {currentSearchQuery ? (
            <Text style={{ fontSize: 10, color: theme.colors.onSurfaceVariant, marginLeft: isSearching ? 6 : 0 }}>
              {currentSearchQuery}
            </Text>
          ) : null}
          {searchCategory ? (
            <Text style={{ fontSize: 10, color: theme.colors.primary, marginLeft: 8, fontWeight: '600' }}>
              {searchCategory}
            </Text>
          ) : null}
          {storeSearchResults.length > 0 && (
            <Text style={{ fontSize: 10, color: theme.colors.onSurfaceVariant, marginLeft: 8 }}>
              {storeSearchResults.length} resultados
            </Text>
          )}
          <View style={{ flex: 1 }} />
          {(currentSearchQuery || storeSearchResults.length > 0) && (
            <TouchableOpacity
              onPress={handleClearSearch}
              activeOpacity={0.7}
            >
              <Icon name="close" size="sm" color={theme.colors.onSurfaceVariant} />
            </TouchableOpacity>
          )}
        </View>
      )}


      {hasActiveSearch && !isSearching && displayProducts.length === 0 ? (
        <View style={[styles.container, GlobalStyles.center, { paddingHorizontal: 24 }] }>
          <Text style={[styles.body, { textAlign: 'center' }]}>{`No encontré productos para ${currentSearchQuery || 'tu búsqueda'}.`}</Text>
          <Text style={[styles.body, { textAlign: 'center', marginTop: 8, color: theme.colors.onSurfaceVariant }]}>
            Prueba con otro término como marca, categoría o beneficio.
          </Text>
        </View>
      ) : (
        <FlatList
          data={displayProducts}
          keyExtractor={(item) => item.id}
          numColumns={retailTemplate === 'grid' ? uiConfig.retailColumns : 1}
          key={retailTemplate}
          renderItem={handleRenderProduct}
          initialNumToRender={8}
          maxToRenderPerBatch={8}
          windowSize={5}
          updateCellsBatchingPeriod={16}
          removeClippedSubviews={true}
          contentContainerStyle={{ paddingBottom: 16, paddingHorizontal: 8 }}
          showsVerticalScrollIndicator={false}
        />
      )}
    </View>
  );
};

// Los estilos ahora vienen del sistema de temas
// Solo definimos estilos específicos si son necesarios
const localStyles = StyleSheet.create({
  // Estilos específicos del RetailScreen si son necesarios
});
