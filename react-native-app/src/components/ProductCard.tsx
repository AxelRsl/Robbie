import React from 'react';
import { View, Text, Image, TouchableOpacity, StyleSheet } from 'react-native';
import type { Product } from '@/types';

interface ProductCardProps {
  product: Product;
  onPress: (product: Product) => void;
  variant?: 'grid' | 'list';
}

export const ProductCard: React.FC<ProductCardProps> = ({ 
  product, 
  onPress, 
  variant = 'grid' 
}) => {
  if (variant === 'list') {
    return (
      <TouchableOpacity 
        style={styles.listContainer} 
        onPress={() => onPress(product)}
        activeOpacity={0.7}
      >
        <Image 
          source={{ uri: product.imageUrl }} 
          style={styles.listImage}
          resizeMode="cover"
        />
        <View style={styles.listContent}>
          <Text style={styles.listTitle} numberOfLines={2}>
            {product.name}
          </Text>
          <Text style={styles.listDescription} numberOfLines={2}>
            {product.description}
          </Text>
          <View style={styles.listFooter}>
            <Text style={styles.price}>
              ${product.price.toLocaleString()} {product.currency}
            </Text>
            <Text style={styles.stock}>
              Stock: {product.stock}
            </Text>
          </View>
        </View>
      </TouchableOpacity>
    );
  }

  return (
    <TouchableOpacity 
      style={styles.gridContainer} 
      onPress={() => onPress(product)}
      activeOpacity={0.7}
    >
      <Image 
        source={{ uri: product.imageUrl }} 
        style={styles.gridImage}
        resizeMode="cover"
      />
      <View style={styles.gridContent}>
        <Text style={styles.gridTitle} numberOfLines={2}>
          {product.name}
        </Text>
        <Text style={styles.gridPrice}>
          ${product.price.toLocaleString()}
        </Text>
        <Text style={styles.gridStock}>
          {product.stock > 0 ? `${product.stock} disponibles` : 'Agotado'}
        </Text>
      </View>
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  gridContainer: {
    flex: 1,
    margin: 8,
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    overflow: 'hidden',
    elevation: 3,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  gridImage: {
    width: '100%',
    height: 180,
    backgroundColor: '#F5F5F5',
  },
  gridContent: {
    padding: 12,
  },
  gridTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#212121',
    marginBottom: 8,
  },
  gridPrice: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#00695C',
    marginBottom: 4,
  },
  gridStock: {
    fontSize: 12,
    color: '#757575',
  },
  listContainer: {
    flexDirection: 'row',
    backgroundColor: '#FFFFFF',
    borderRadius: 12,
    marginHorizontal: 16,
    marginVertical: 8,
    overflow: 'hidden',
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
  },
  listImage: {
    width: 120,
    height: 120,
    backgroundColor: '#F5F5F5',
  },
  listContent: {
    flex: 1,
    padding: 12,
    justifyContent: 'space-between',
  },
  listTitle: {
    fontSize: 16,
    fontWeight: '600',
    color: '#212121',
    marginBottom: 4,
  },
  listDescription: {
    fontSize: 13,
    color: '#757575',
    marginBottom: 8,
  },
  listFooter: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  price: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#00695C',
  },
  stock: {
    fontSize: 12,
    color: '#757575',
  },
});
