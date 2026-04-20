import React, { useState, useEffect } from 'react';
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

const { ProductSearchModule } = NativeModules;

export const RetailScreen: React.FC = () => {
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchResults, setSearchResults] = useState<Product[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  
  const { 
    retailTemplate, 
    setRetailTemplate, 
    setSelectedProduct, 
    setCurrentMode, 
    uiConfig,
    searchRecommendation,
    setSearchRecommendation,
    setSearchResults: setStoreSearchResults
  } = useAppStore();

  useEffect(() => {
    loadProducts();
    
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
        if (event.recommendation) {
          setSearchRecommendation(event.recommendation);
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
      modeSwitchListener.remove();
    };
  }, []);

  const loadProducts = async () => {
    console.log('[RetailScreen] Iniciando carga de productos...');
    setLoading(true);
    try {
      const data = await CloudApi.getProducts();
      console.log('[RetailScreen] Productos cargados:', data.length, 'items');
      if (data.length > 0) {
        console.log('[RetailScreen] Primeros 3 productos:', data.slice(0, 3).map(p => p.name));
      }
      setProducts(data);
    } catch (error) {
      console.error('[RetailScreen] Error loading products:', error);
    } finally {
      setLoading(false);
    }
  };

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
        
        await RobotBridge.say(`Encontré ${results.length} productos relacionados con ${query}`);
      } else {
        // Fallback a CloudApi si el módulo no está disponible
        const results = await CloudApi.searchProducts(query);
        console.log('[RetailScreen] Resultados de busqueda CloudApi:', results.length);
        setSearchResults(results);
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

  const displayProducts = searchResults.length > 0 ? searchResults : products;

  if (loading) {
    return (
      <View style={styles.centerContainer}>
        <ActivityIndicator size="large" color="#00695C" />
        <Text style={styles.loadingText}>Cargando productos...</Text>
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => setCurrentMode('menu')} activeOpacity={0.7}>
          <Text style={styles.backButton}>← Menu</Text>
        </TouchableOpacity>
        <Text style={styles.title}>Modo Minorista</Text>
        <TouchableOpacity 
          style={styles.templateButton} 
          onPress={toggleTemplate}
          activeOpacity={0.7}
        >
          <Text style={styles.templateButtonText}>
            {retailTemplate === 'grid' ? '☰ Lista' : '⊞ Cuadricula'}
          </Text>
        </TouchableOpacity>
      </View>

      {uiConfig.showSearchBar && (
        <SearchBar 
          onSearch={handleSearch} 
          onVoiceSearch={uiConfig.showMicButton ? handleVoiceSearch : undefined}
          placeholder="Buscar productos con voz o texto..."
        />
      )}

      {isSearching && (
        <View style={styles.searchingContainer}>
          <ActivityIndicator size="small" color="#00695C" />
          <Text style={styles.searchingText}>Buscando...</Text>
        </View>
      )}

      {searchRecommendation && (
        <View style={styles.recommendationContainer}>
          <Text style={styles.recommendationLabel}>Recomendación:</Text>
          <Text style={styles.recommendationText}>{searchRecommendation}</Text>
        </View>
      )}

      {searchResults.length > 0 && (
        <View style={styles.resultsHeader}>
          <Text style={styles.resultsText}>
            {searchResults.length} resultados encontrados
          </Text>
          <TouchableOpacity onPress={() => {
            setSearchResults([]);
            setStoreSearchResults([]);
            setSearchRecommendation('');
          }}>
            <Text style={styles.clearButton}>Limpiar</Text>
          </TouchableOpacity>
        </View>
      )}

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
        contentContainerStyle={styles.listContent}
        showsVerticalScrollIndicator={false}
      />
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 8,
    backgroundColor: '#FFFFFF',
    borderBottomWidth: 1,
    borderBottomColor: '#E0E0E0',
  },
  backButton: {
    fontSize: 14,
    color: '#00695C',
    fontWeight: '600',
  },
  title: {
    fontSize: 16,
    fontWeight: 'bold',
    color: '#212121',
  },
  templateButton: {
    paddingHorizontal: 10,
    paddingVertical: 5,
    backgroundColor: '#00695C',
    borderRadius: 16,
  },
  templateButtonText: {
    color: '#FFFFFF',
    fontSize: 11,
    fontWeight: '600',
  },
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F5F5F5',
  },
  loadingText: {
    marginTop: 8,
    fontSize: 14,
    color: '#757575',
  },
  searchingContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 8,
    backgroundColor: '#E8F5E9',
  },
  searchingText: {
    marginLeft: 6,
    fontSize: 12,
    color: '#00695C',
  },
  resultsHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 8,
    backgroundColor: '#E3F2FD',
  },
  resultsText: {
    fontSize: 12,
    color: '#1976D2',
    fontWeight: '600',
  },
  clearButton: {
    fontSize: 12,
    color: '#1976D2',
    textDecorationLine: 'underline',
  },
  recommendationContainer: {
    padding: 12,
    backgroundColor: '#FFF3E0',
    borderLeftWidth: 4,
    borderLeftColor: '#FF9800',
    marginHorizontal: 8,
    marginTop: 8,
    marginBottom: 4,
  },
  recommendationLabel: {
    fontSize: 12,
    color: '#E65100',
    fontWeight: '700',
    marginBottom: 4,
  },
  recommendationText: {
    fontSize: 14,
    color: '#424242',
    lineHeight: 20,
  },
  listContent: {
    paddingBottom: 8,
    paddingHorizontal: 4,
  },
});
