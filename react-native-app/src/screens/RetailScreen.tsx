import React, { useEffect, useState } from 'react';
import {
  View,
  FlatList,
  StyleSheet,
  Text,
  TouchableOpacity,
  ActivityIndicator,
  NativeModules,
  DeviceEventEmitter,
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
  const [searchResults, setSearchResults] = useState<Product[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  const [currentSearchQuery, setCurrentSearchQuery] = useState<string>('');
  const [searchCategory, setSearchCategory] = useState<string>('');
  const { theme } = useTheme();
  const styles = createStyles(theme);
  
  const { 
    retailTemplate, 
    setRetailTemplate, 
    setSelectedProduct, 
    setCurrentMode, 
    uiConfig,
    searchRecommendation,
    setSearchRecommendation,
    setSearchResults: setStoreSearchResults,
    products,
    productsLoaded,
  } = useAppStore();

  useEffect(() => {
    // Establecer color Ikalp sólido al cargar la pantalla
    LedHelper.setIkalpPrimary().catch(error => {
      console.warn('[RetailScreen] No se pudo establecer color Ikalp:', error);
    });
    
    // Escuchar eventos de búsqueda desde EveActivity (LLM)
    const productSearchListener = DeviceEventEmitter.addListener('onProductSearch', (event) => {
      console.log('[RetailScreen] Product search event from LLM:', event);
      if (event.products && Array.isArray(event.products)) {
        setSearchResults(event.products);
        setStoreSearchResults(event.products);
        
        // Actualizar información de búsqueda
        if (event.query) {
          setCurrentSearchQuery(event.query);
        }
        if (event.category) {
          setSearchCategory(event.category);
        } else if (event.products.length > 0) {
          // Inferir categoría del primer producto si no se proporciona
          setSearchCategory(event.products[0].category || '');
        }
        
        if (event.recommendation) {
          setSearchRecommendation(event.recommendation);
        }
      }
    });

    // Escuchar eventos de recomendación desde EveActivity (LLM)
    const productRecommendationListener = DeviceEventEmitter.addListener('onProductRecommendation', (event) => {
      console.log('[RetailScreen] Product recommendation event from LLM:', event);
      if (event.products && Array.isArray(event.products)) {
        // Mostrar todos los productos con flag aiRecommended
        setSearchResults(event.products);
        setStoreSearchResults(event.products);
        
        // Actualizar información de recomendación
        setCurrentSearchQuery('Recomendaciones AI');
        if (event.products.length > 0) {
          // Obtener categorías de productos recomendados
          const categories = [...new Set(event.products
            .filter((p: any) => p.aiRecommended)
            .map((p: any) => p.category)
            .filter(Boolean))];
          setSearchCategory(categories.join(', ') || 'Varios');
        }
        
        if (event.explanation) {
          setSearchRecommendation(event.explanation);
        }
      }
    });

    // Escuchar eventos de cambio de modo automático
    const modeSwitchListener = DeviceEventEmitter.addListener('onModeSwitch', (event) => {
      console.log('[RetailScreen] Mode switch event from LLM:', event);
      if (event.mode === 'retail' && event.autoSwitch) {
        console.log('[RetailScreen] Auto-switching to retail mode');
        setCurrentMode('retail');
      }
    });

    return () => {
      productSearchListener.remove();
      productRecommendationListener.remove();
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
        setSearchResults(results);
        setStoreSearchResults(results);
        
        // Actualizar información de búsqueda
        setCurrentSearchQuery(query);
        if (results.length > 0) {
          const categories = [...new Set(results.map((p: Product) => p.category).filter(Boolean))];
          setSearchCategory(categories.join(', ') || '');
        }
        
        await RobotBridge.say(`Encontré ${results.length} productos relacionados con ${query}`);
      } else {
        // Fallback a CloudApi si el módulo no está disponible
        const results = await CloudApi.searchProducts(query);
        console.log('[RetailScreen] Resultados de busqueda CloudApi:', results.length);
        setSearchResults(results);
        setStoreSearchResults(results);
        
        // Actualizar información de búsqueda
        setCurrentSearchQuery(query);
        if (results.length > 0) {
          const categories = [...new Set(results.map((p: Product) => p.category).filter(Boolean))];
          setSearchCategory(categories.join(', ') || '');
        }
        
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

  const displayProducts = searchResults.length > 0 ? searchResults : products;

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
          {searchResults.length > 0 && (
            <Text style={{ fontSize: 10, color: theme.colors.onSurfaceVariant, marginLeft: 8 }}>
              {searchResults.length} resultados
            </Text>
          )}
          <View style={{ flex: 1 }} />
          {(currentSearchQuery || searchResults.length > 0) && (
            <TouchableOpacity
              onPress={() => {
                setCurrentSearchQuery('');
                setSearchCategory('');
                setSearchResults([]);
                setStoreSearchResults([]);
                setSearchRecommendation('');
              }}
              activeOpacity={0.7}
            >
              <Icon name="close" size="sm" color={theme.colors.onSurfaceVariant} />
            </TouchableOpacity>
          )}
        </View>
      )}

      {/* Recomendacion minimalista */}
      {searchRecommendation ? (
        <View style={{ paddingHorizontal: 16, paddingBottom: 6 }}>
          <Text style={{ fontSize: 10, color: theme.colors.warning, fontWeight: '600' }} numberOfLines={2}>
            {searchRecommendation}
          </Text>
        </View>
      ) : null}

      <FlatList
        data={displayProducts}
        keyExtractor={(item) => item.id}
        numColumns={retailTemplate === 'grid' ? uiConfig.retailColumns : 1}
        key={retailTemplate}
        renderItem={({ item }) => (
          <ProductCard 
            product={item} 
            onPress={handleProductPress}
            variant={retailTemplate}
          />
        )}
        contentContainerStyle={{ paddingBottom: 16, paddingHorizontal: 8 }}
        showsVerticalScrollIndicator={false}
      />
    </View>
  );
};

// Los estilos ahora vienen del sistema de temas
// Solo definimos estilos específicos si son necesarios
const localStyles = StyleSheet.create({
  // Estilos específicos del RetailScreen si son necesarios
});
