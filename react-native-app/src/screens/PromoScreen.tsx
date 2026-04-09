import React, { useState, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  Dimensions,
  ActivityIndicator,
  Image,
} from 'react-native';
import { CloudApi } from '@/services/CloudApi';
import { useAppStore } from '@/stores/useAppStore';
import type { Promotion, PromoVideo } from '@/types';

const { width } = Dimensions.get('window');

export const PromoScreen: React.FC = () => {
  const [promotions, setPromotions] = useState<Promotion[]>([]);
  const [videos, setVideos] = useState<PromoVideo[]>([]);
  const [loading, setLoading] = useState(true);
  const [currentVideoIndex, setCurrentVideoIndex] = useState(0);
  
  const { promoTemplate, setPromoTemplate, setCurrentMode } = useAppStore();

  useEffect(() => {
    loadPromotions();
  }, []);

  const loadPromotions = async () => {
    setLoading(true);
    try {
      const [promoData, videoData] = await Promise.all([
        CloudApi.getPromotions(),
        CloudApi.getPromoVideos(),
      ]);
      setPromotions(promoData);
      setVideos(videoData);
    } catch (error) {
      console.error('Error loading promotions:', error);
    } finally {
      setLoading(false);
    }
  };

  const toggleTemplate = () => {
    setPromoTemplate(promoTemplate === 'video' ? 'carousel' : 'video');
  };

  if (loading) {
    return (
      <View style={styles.centerContainer}>
        <ActivityIndicator size="large" color="#FF5722" />
        <Text style={styles.loadingText}>Cargando promociones...</Text>
      </View>
    );
  }

  if (promoTemplate === 'video') {
    return (
      <View style={styles.container}>
        <View style={styles.header}>
          <TouchableOpacity onPress={() => setCurrentMode('menu')} activeOpacity={0.7}>
            <Text style={styles.backButton}>← Menu</Text>
          </TouchableOpacity>
          <Text style={styles.title}>Promociones - Video</Text>
          <TouchableOpacity style={styles.templateButton} onPress={toggleTemplate}>
            <Text style={styles.templateButtonText}>⊞ Carrusel</Text>
          </TouchableOpacity>
        </View>

        {videos.length > 0 && (
          <View style={styles.videoContainer}>
            <Image
              source={{ uri: videos[currentVideoIndex].thumbnailUrl }}
              style={styles.video}
              resizeMode="cover"
            />
            <View style={styles.playOverlay}>
              <Text style={styles.playIcon}>▶</Text>
            </View>

            <View style={styles.videoInfo}>
              <Text style={styles.videoTitle}>
                {videos[currentVideoIndex].title}
              </Text>
              <Text style={styles.videoDescription}>
                {videos[currentVideoIndex].description}
              </Text>
              <Text style={styles.videoDuration}>
                Duracion: {videos[currentVideoIndex].duration}s
              </Text>
            </View>
            
            <View style={styles.videoControls}>
              <TouchableOpacity
                style={[styles.navButton, currentVideoIndex === 0 && styles.navButtonDisabled]}
                onPress={() => setCurrentVideoIndex(Math.max(0, currentVideoIndex - 1))}
                disabled={currentVideoIndex === 0}
              >
                <Text style={styles.navButtonText}>← Anterior</Text>
              </TouchableOpacity>
              
              <Text style={styles.videoCounter}>
                {currentVideoIndex + 1} / {videos.length}
              </Text>
              
              <TouchableOpacity
                style={[styles.navButton, currentVideoIndex === videos.length - 1 && styles.navButtonDisabled]}
                onPress={() => setCurrentVideoIndex(Math.min(videos.length - 1, currentVideoIndex + 1))}
                disabled={currentVideoIndex === videos.length - 1}
              >
                <Text style={styles.navButtonText}>Siguiente →</Text>
              </TouchableOpacity>
            </View>
          </View>
        )}
      </View>
    );
  }

  return (
    <View style={styles.container}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => setCurrentMode('menu')} activeOpacity={0.7}>
          <Text style={styles.backButton}>← Menu</Text>
        </TouchableOpacity>
        <Text style={styles.title}>Promociones - Carrusel</Text>
        <TouchableOpacity style={styles.templateButton} onPress={toggleTemplate}>
          <Text style={styles.templateButtonText}>▶ Video</Text>
        </TouchableOpacity>
      </View>

      <ScrollView style={styles.scrollView}>
        {promotions.map((promo) => (
          <View key={promo.id} style={styles.promoCard}>
            <View style={styles.promoHeader}>
              <Text style={styles.promoTitle}>{promo.title}</Text>
              <View style={styles.discountBadge}>
                <Text style={styles.discountText}>{promo.discount}% OFF</Text>
              </View>
            </View>
            
            <Text style={styles.promoDescription}>{promo.description}</Text>
            
            <View style={styles.promoDates}>
              <Text style={styles.dateText}>
                Válido del {new Date(promo.startDate).toLocaleDateString()} 
                {' al '} 
                {new Date(promo.endDate).toLocaleDateString()}
              </Text>
            </View>
            
            <ScrollView 
              horizontal 
              showsHorizontalScrollIndicator={false}
              style={styles.productsScroll}
            >
              {promo.products.map((product) => (
                <View key={product.id} style={styles.miniProductCard}>
                  <Text style={styles.miniProductName} numberOfLines={2}>
                    {product.name}
                  </Text>
                  <Text style={styles.miniProductPrice}>
                    ${product.price.toLocaleString()}
                  </Text>
                </View>
              ))}
            </ScrollView>
          </View>
        ))}
      </ScrollView>
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
    color: '#FF5722',
    fontWeight: '600',
  },
  title: {
    fontSize: 18,
    fontWeight: 'bold',
    color: '#212121',
  },
  templateButton: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    backgroundColor: '#FF5722',
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
  videoContainer: {
    flex: 1,
    backgroundColor: '#000000',
  },
  video: {
    width: width,
    height: width * (9 / 16),
  },
  playOverlay: {
    position: 'absolute',
    top: 60,
    left: 0,
    right: 0,
    height: width * (9 / 16),
    justifyContent: 'center',
    alignItems: 'center',
  },
  playIcon: {
    fontSize: 64,
    color: 'rgba(255,255,255,0.8)',
  },
  videoInfo: {
    padding: 16,
    backgroundColor: '#FFFFFF',
  },
  videoTitle: {
    fontSize: 20,
    fontWeight: 'bold',
    color: '#212121',
    marginBottom: 8,
  },
  videoDescription: {
    fontSize: 14,
    color: '#757575',
  },
  videoDuration: {
    fontSize: 12,
    color: '#9E9E9E',
    marginTop: 4,
  },
  videoControls: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 16,
    backgroundColor: '#FFFFFF',
  },
  navButton: {
    paddingHorizontal: 16,
    paddingVertical: 8,
    backgroundColor: '#FF5722',
    borderRadius: 20,
  },
  navButtonText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: '600',
  },
  navButtonDisabled: {
    opacity: 0.4,
  },
  videoCounter: {
    fontSize: 16,
    color: '#757575',
  },
  scrollView: {
    flex: 1,
  },
  promoCard: {
    backgroundColor: '#FFFFFF',
    margin: 16,
    borderRadius: 12,
    padding: 16,
    elevation: 3,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.1,
    shadowRadius: 4,
  },
  promoHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 12,
  },
  promoTitle: {
    flex: 1,
    fontSize: 20,
    fontWeight: 'bold',
    color: '#212121',
  },
  discountBadge: {
    backgroundColor: '#FF5722',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 16,
  },
  discountText: {
    color: '#FFFFFF',
    fontSize: 14,
    fontWeight: 'bold',
  },
  promoDescription: {
    fontSize: 14,
    color: '#757575',
    marginBottom: 12,
  },
  promoDates: {
    paddingVertical: 8,
    borderTopWidth: 1,
    borderTopColor: '#E0E0E0',
    marginBottom: 12,
  },
  dateText: {
    fontSize: 12,
    color: '#9E9E9E',
  },
  productsScroll: {
    marginTop: 8,
  },
  miniProductCard: {
    width: 120,
    padding: 12,
    marginRight: 12,
    backgroundColor: '#F5F5F5',
    borderRadius: 8,
  },
  miniProductName: {
    fontSize: 12,
    fontWeight: '600',
    color: '#212121',
    marginBottom: 4,
  },
  miniProductPrice: {
    fontSize: 14,
    fontWeight: 'bold',
    color: '#FF5722',
  },
});
