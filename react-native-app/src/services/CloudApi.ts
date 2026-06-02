import { NativeModules } from 'react-native';
import type { Product, Promotion, PromoVideo, SceneProject } from '@/types';

const { CloudApiModule, ProductsModule, RobbieConfig } = NativeModules;

class CloudApiService {
  private connected = false;

  async initialize() {
    try {
      console.log('[CloudApi] Conectando a nube OrionStar...');
      this.connected = await CloudApiModule.connect();
      
      const statusJson = await CloudApiModule.getStatus();
      const status = JSON.parse(statusJson);
      console.log('[CloudApi] Estado:', JSON.stringify(status));
    } catch (error) {
      console.warn('[CloudApi] Error inicializando, usando fallback:', error);
      this.connected = false;
    }
  }

  async getProducts(category?: string): Promise<Product[]> {
    console.log('[CloudApi] getProducts llamado, category:', category || 'todas');
    try {
      const json = await ProductsModule.getProducts(category || '');
      console.log('[CloudApi] Respuesta raw de ProductsModule:', json?.substring(0, 200));
      const products = JSON.parse(json);
      console.log('[CloudApi] Productos parseados:', products.length, 'items');
      if (products.length > 0) {
        console.log('[CloudApi] Primer producto:', products[0].name);
      }
      return products;
    } catch (error) {
      console.error('[CloudApi] Error obteniendo productos:', error);
      return [];
    }
  }

  async searchProducts(query: string): Promise<Product[]> {
    console.log('[CloudApi] searchProducts llamado, query:', query);
    try {
      const json = await ProductsModule.searchProducts(query);
      console.log('[CloudApi] Respuesta de busqueda:', json?.substring(0, 200));
      const result = JSON.parse(json);
      console.log('[CloudApi] Resultados encontrados:', result.products?.length || 0);
      return result.products || [];
    } catch (error) {
      console.error('[CloudApi] Error buscando productos:', error);
      return [];
    }
  }

  async getConfig(): Promise<any> {
    console.log('[CloudApi] getConfig llamado');
    try {
      const config = await RobbieConfig.getConfig();
      console.log('[CloudApi] Configuración recibida');
      console.log('[CloudApi] Config - validScreens:', config?.validScreens);
      return config;
    } catch (error) {
      console.error('[CloudApi] Error obteniendo configuración:', error);
      return { validScreens: [] };
    }
  }

  async getPromotions(): Promise<Promotion[]> {
    try {
      const products = await this.getProducts();
      return this.buildPromotionsFromProducts(products);
    } catch (error) {
      console.error('[CloudApi] Error obteniendo promociones:', error);
      return [];
    }
  }

  async getPromoVideos(): Promise<PromoVideo[]> {
    return [
      {
        id: 'video1',
        title: 'Nuevos Productos 2026',
        videoUrl: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4',
        thumbnailUrl: 'https://via.placeholder.com/640x360/FF5722/FFFFFF?text=Video+1',
        duration: 120,
        description: 'Descubre nuestros nuevos productos',
      },
      {
        id: 'video2',
        title: 'Ofertas Especiales',
        videoUrl: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4',
        thumbnailUrl: 'https://via.placeholder.com/640x360/3F51B5/FFFFFF?text=Video+2',
        duration: 90,
        description: 'No te pierdas estas ofertas',
      },
    ];
  }

  async getActiveSceneProject(): Promise<SceneProject | null> {
    console.log('[CloudApi] getActiveSceneProject llamado');
    try {
      const response = await fetch('http://127.0.0.1:8080/api/scene-projects/active');
      if (!response.ok) {
        console.log('[CloudApi] No hay proyecto de escena activo');
        return null;
      }
      const project = await response.json();
      console.log('[CloudApi] Proyecto activo:', project.name, '- template:', project.templateType);
      return project;
    } catch (error) {
      console.warn('[CloudApi] Error obteniendo proyecto de escena:', error);
      return null;
    }
  }

  async refreshProducts(): Promise<Product[]> {
    try {
      const json = await ProductsModule.refreshProducts();
      return JSON.parse(json);
    } catch (error) {
      console.error('[CloudApi] Error refrescando productos:', error);
      return [];
    }
  }

  async apiGet(endpoint: string): Promise<any> {
    try {
      const json = await CloudApiModule.apiGet(endpoint);
      return JSON.parse(json);
    } catch (error) {
      console.error('[CloudApi] Error en GET:', error);
      throw error;
    }
  }

  async apiPost(endpoint: string, body: any): Promise<any> {
    try {
      const json = await CloudApiModule.apiPost(endpoint, JSON.stringify(body));
      return JSON.parse(json);
    } catch (error) {
      console.error('[CloudApi] Error en POST:', error);
      throw error;
    }
  }

  private buildPromotionsFromProducts(products: Product[]): Promotion[] {
    if (products.length === 0) return [];
    
    const half = Math.ceil(products.length / 2);
    return [
      {
        id: 'promo1',
        title: 'Descuento en Electronica',
        description: 'Hasta 30% de descuento en productos seleccionados',
        imageUrl: 'https://via.placeholder.com/800x400/4CAF50/FFFFFF?text=Promo',
        products: products.slice(0, half),
        startDate: '2026-04-01',
        endDate: '2026-04-30',
        discount: 30,
      },
      {
        id: 'promo2',
        title: 'Ofertas de Temporada',
        description: 'Productos con envio gratis',
        products: products.slice(half),
        startDate: '2026-04-01',
        endDate: '2026-04-15',
        discount: 20,
      },
    ];
  }
}

export const CloudApi = new CloudApiService();
