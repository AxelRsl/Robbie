import React from 'react';
import { View, Text, Image, TouchableOpacity, StyleSheet } from 'react-native';
import type { Product } from '@/types';

interface ProductCardProps {
  product: Product;
  onPress: (product: Product) => void;
  variant?: 'grid' | 'list';
}

export const ProductCard: React.FC<ProductCardProps> = React.memo(({ 
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
              {product.inStock !== false ? 'Disponible' : 'Agotado'}
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
          {product.inStock !== false ? 'Disponible' : 'Agotado'}
        </Text>
      </View>
    </TouchableOpacity>
  );
});

ProductCard.displayName = 'ProductCard';

const styles = StyleSheet.create({
  gridContainer: {
    flex: 1,
    margin: 4,
    backgroundColor: '#FFFFFF',
    borderRadius: 8,
    overflow: 'hidden',
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
    maxWidth: '24%',
  },
  gridImage: {
    width: '100%',
    height: 80,
    backgroundColor: '#F5F5F5',
  },
  gridContent: {
    padding: 6,
  },
  gridTitle: {
    fontSize: 10,
    fontWeight: '600',
    color: '#212121',
    marginBottom: 4,
  },
  gridPrice: {
    fontSize: 12,
    fontWeight: 'bold',
    color: '#00695C',
    marginBottom: 2,
  },
  gridStock: {
    fontSize: 9,
    color: '#757575',
  },
  listContainer: {
    flexDirection: 'row',
    backgroundColor: '#FFFFFF',
    borderRadius: 8,
    marginHorizontal: 8,
    marginVertical: 4,
    overflow: 'hidden',
    elevation: 2,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.1,
    shadowRadius: 2,
  },
  listImage: {
    width: 80,
    height: 80,
    backgroundColor: '#F5F5F5',
  },
  listContent: {
    flex: 1,
    padding: 8,
    justifyContent: 'space-between',
  },
  listTitle: {
    fontSize: 12,
    fontWeight: '600',
    color: '#212121',
    marginBottom: 2,
  },
  listDescription: {
    fontSize: 10,
    color: '#757575',
    marginBottom: 4,
  },
  listFooter: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  price: {
    fontSize: 13,
    fontWeight: 'bold',
    color: '#00695C',
  },
  stock: {
    fontSize: 10,
    color: '#757575',
  },
});
