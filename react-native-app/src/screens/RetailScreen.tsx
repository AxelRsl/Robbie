import React, { useState, useEffect } from 'react';
import {
  View,
  FlatList,
  StyleSheet,
  Text,
  TouchableOpacity,
  ActivityIndicator,
} from 'react-native';
import { ProductCard } from '@/components/ProductCard';
import { SearchBar } from '@/components/SearchBar';
import { CloudApi } from '@/services/CloudApi';
import { RobotBridge } from '@/services/RobotBridge';
import { useAppStore } from '@/stores/useAppStore';
import type { Product } from '@/types';

export const RetailScreen: React.FC = () => {
  const [products, setProducts] = useState<Product[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchResults, setSearchResults] = useState<Product[]>([]);
  const [isSearching, setIsSearching] = useState(false);
  
  const { retailTemplate, setRetailTemplate, setSelectedProduct, setCurrentMode } = useAppStore();

  useEffect(() => {
    loadProducts();
  }, []);

  const loadProducts = async () => {
    setLoading(true);
    try {
      const data = await CloudApi.getProducts();
      setProducts(data);
    } catch (error) {
      console.error('Error loading products:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleSearch = async (query: string) => {
    setIsSearching(true);
    try {
      const results = await CloudApi.searchProducts(query);
      setSearchResults(results);
      
      await RobotBridge.say(`Encontré ${results.length} productos relacionados con ${query}`);
    } catch (error) {
      console.error('Error searching:', error);
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

      <SearchBar 
        onSearch={handleSearch} 
        onVoiceSearch={handleVoiceSearch}
        placeholder="Buscar productos con voz o texto..."
      />

      {isSearching && (
        <View style={styles.searchingContainer}>
          <ActivityIndicator size="small" color="#00695C" />
          <Text style={styles.searchingText}>Buscando...</Text>
        </View>
      )}

      {searchResults.length > 0 && (
        <View style={styles.resultsHeader}>
          <Text style={styles.resultsText}>
            {searchResults.length} resultados encontrados
          </Text>
          <TouchableOpacity onPress={() => setSearchResults([])}>
            <Text style={styles.clearButton}>Limpiar</Text>
          </TouchableOpacity>
        </View>
      )}

      <FlatList
        data={displayProducts}
        keyExtractor={(item) => item.id}
        numColumns={retailTemplate === 'grid' ? 2 : 1}
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
    padding: 16,
    backgroundColor: '#FFFFFF',
    borderBottomWidth: 1,
    borderBottomColor: '#E0E0E0',
  },
  backButton: {
    fontSize: 16,
    color: '#00695C',
    fontWeight: '600',
  },
  title: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#212121',
  },
  templateButton: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    backgroundColor: '#00695C',
    borderRadius: 20,
  },
  templateButtonText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '600',
  },
  centerContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#F5F5F5',
  },
  loadingText: {
    marginTop: 12,
    fontSize: 16,
    color: '#757575',
  },
  searchingContainer: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 12,
    backgroundColor: '#E8F5E9',
  },
  searchingText: {
    marginLeft: 8,
    fontSize: 14,
    color: '#00695C',
  },
  resultsHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 12,
    backgroundColor: '#E3F2FD',
  },
  resultsText: {
    fontSize: 14,
    color: '#1976D2',
    fontWeight: '600',
  },
  clearButton: {
    fontSize: 14,
    color: '#1976D2',
    textDecorationLine: 'underline',
  },
  listContent: {
    paddingBottom: 16,
  },
});
