import { NativeModules } from 'react-native';
import type { RobotConfig, Product, SearchResult, OPKPlugin } from '@/types';

const { 
  RobotConfigModule, 
  RobotSkillModule, 
  CloudApiModule,
  ProductsModule,
  AgentModule,
  LedModule,
} = NativeModules;

export class RobotBridge {
  
  static async getConfig(): Promise<RobotConfig> {
    try {
      const configJson = await RobotConfigModule.getConfig();
      return JSON.parse(configJson);
    } catch (error) {
      console.error('Error getting config:', error);
      throw error;
    }
  }
  
  static async updateConfig(config: Partial<RobotConfig>): Promise<void> {
    try {
      await RobotConfigModule.updateConfig(JSON.stringify(config));
    } catch (error) {
      console.error('Error updating config:', error);
      throw error;
    }
  }
  
  static async executeAction(action: string, params: any = {}): Promise<any> {
    try {
      const result = await RobotSkillModule.executeAction(action, JSON.stringify(params));
      return JSON.parse(result);
    } catch (error) {
      console.error('Error executing action:', error);
      throw error;
    }
  }
  
  static async say(text: string): Promise<void> {
    return this.executeAction('orion.agent.action.SAY', { text });
  }
  
  static async navigate(destination: string): Promise<void> {
    return this.executeAction('orion.agent.action.NAVIGATE_REC_START', { destination });
  }
  
  static async connectToCloud(): Promise<boolean> {
    try {
      return await CloudApiModule.connect();
    } catch (error) {
      console.error('Error connecting to cloud:', error);
      return false;
    }
  }
  
  static async getCloudStatus(): Promise<{connected: boolean; region: string}> {
    try {
      const statusJson = await CloudApiModule.getStatus();
      return JSON.parse(statusJson);
    } catch (error) {
      console.error('Error getting cloud status:', error);
      return { connected: false, region: 'unknown' };
    }
  }
  
  static async searchProducts(query: string): Promise<SearchResult> {
    try {
      const resultJson = await ProductsModule.searchProducts(query);
      return JSON.parse(resultJson);
    } catch (error) {
      console.error('Error searching products:', error);
      return { products: [], query, totalResults: 0 };
    }
  }
  
  static async getProducts(category?: string): Promise<Product[]> {
    try {
      const productsJson = await ProductsModule.getProducts(category || '');
      return JSON.parse(productsJson);
    } catch (error) {
      console.error('Error getting products:', error);
      return [];
    }
  }
  
  static async getProductDetails(productId: string): Promise<Product | null> {
    try {
      const productJson = await ProductsModule.getProductDetails(productId);
      return JSON.parse(productJson);
    } catch (error) {
      console.error('Error getting product details:', error);
      return null;
    }
  }

  // --- AgentOS (Agent SDK) ---
  // El mic se abre automaticamente cuando la app esta en foreground.
  // ASR/TTS/LLM son gestionados internamente por AgentOS.
  // Estos metodos exponen las APIs utilitarias de AgentCore.

  static async query(text: string): Promise<void> {
    try {
      await AgentModule.query(text);
    } catch (error) {
      console.error('Error AgentCore.query:', error);
      throw error;
    }
  }

  static async tts(text: string): Promise<void> {
    try {
      await AgentModule.tts(text);
    } catch (error) {
      console.error('Error AgentCore.tts:', error);
      throw error;
    }
  }

  static async stopTTS(): Promise<void> {
    try {
      await AgentModule.stopTTS();
    } catch (error) {
      console.error('Error AgentCore.stopTTS:', error);
    }
  }

  static async setMicMuted(muted: boolean): Promise<void> {
    try {
      await AgentModule.setMicMuted(muted);
    } catch (error) {
      console.error('Error AgentCore.setMicMuted:', error);
    }
  }

  static async uploadInterfaceInfo(info: string): Promise<void> {
    try {
      await AgentModule.uploadInterfaceInfo(info);
    } catch (error) {
      console.error('Error AgentCore.uploadInterfaceInfo:', error);
    }
  }

  static async clearContext(): Promise<void> {
    try {
      await AgentModule.clearContext();
    } catch (error) {
      console.error('Error AgentCore.clearContext:', error);
    }
  }

  static async getAvailablePlugins(): Promise<OPKPlugin[]> {
    try {
      const json = await AgentModule.getAvailablePlugins();
      return JSON.parse(json);
    } catch (error) {
      console.error('Error getting plugins:', error);
      return [];
    }
  }

  // --- Control de LEDs ---
  
  static async setLedColor(hexColor: string): Promise<void> {
    try {
      await LedModule.setSolidColor(hexColor);
      console.log('[RobotBridge] Color LED establecido:', hexColor);
    } catch (error) {
      console.error('Error estableciendo color LED:', error);
      throw error;
    }
  }

  static async startLedEffect(effect: string, hexColor: string): Promise<void> {
    try {
      await LedModule.startEffect(effect, hexColor);
      console.log('[RobotBridge] Efecto LED iniciado:', effect, hexColor);
    } catch (error) {
      console.error('Error iniciando efecto LED:', error);
      throw error;
    }
  }

  static async stopLedEffect(): Promise<void> {
    try {
      await LedModule.stopEffect();
      console.log('[RobotBridge] Efecto LED detenido');
    } catch (error) {
      console.error('Error deteniendo efecto LED:', error);
      throw error;
    }
  }

  static async restoreLedDefault(): Promise<void> {
    try {
      await LedModule.restoreDefault();
      console.log('[RobotBridge] LEDs restaurados a color por defecto');
    } catch (error) {
      console.error('Error restaurando LEDs:', error);
      throw error;
    }
  }

  static async setLedBrightness(brightness: number): Promise<void> {
    try {
      await LedModule.setBrightness(brightness);
      console.log('[RobotBridge] Brillo LED establecido:', brightness);
    } catch (error) {
      console.error('Error estableciendo brillo LED:', error);
      throw error;
    }
  }

  static async getLedStatus(): Promise<any> {
    try {
      const statusJson = await LedModule.getStatus();
      return JSON.parse(statusJson);
    } catch (error) {
      console.error('Error obteniendo estado LED:', error);
      return null;
    }
  }

  // --- Efectos LED Predefinidos (API Oficial OrionStar) ---

  static async setPredefinedLedEffect(effect: number): Promise<void> {
    try {
      await LedModule.setPredefinedEffect(effect);
      console.log('[RobotBridge] Efecto predefinido LED aplicado:', '0x' + effect.toString(16));
    } catch (error) {
      console.error('Error aplicando efecto predefinido LED:', error);
      throw error;
    }
  }

  static async setGreenBreathingEffect(): Promise<void> {
    try {
      await LedModule.setGreenBreathingEffect();
      console.log('[RobotBridge] Efecto respiración verde activado');
    } catch (error) {
      console.error('Error activando efecto respiración verde:', error);
      throw error;
    }
  }

  static async setBlueBreathingEffect(): Promise<void> {
    try {
      await LedModule.setBlueBreathingEffect();
      console.log('[RobotBridge] Efecto respiración azul activado');
    } catch (error) {
      console.error('Error activando efecto respiración azul:', error);
      throw error;
    }
  }

  static async setRedNormalEffect(): Promise<void> {
    try {
      await LedModule.setRedNormalEffect();
      console.log('[RobotBridge] Efecto normal rojo activado');
    } catch (error) {
      console.error('Error activando efecto normal rojo:', error);
      throw error;
    }
  }

  static async setTurnRightEffect(): Promise<void> {
    try {
      await LedModule.setTurnRightEffect();
      console.log('[RobotBridge] Efecto giro derecha activado');
    } catch (error) {
      console.error('Error activando efecto giro derecha:', error);
      throw error;
    }
  }

  static async setTurnLeftEffect(): Promise<void> {
    try {
      await LedModule.setTurnLeftEffect();
      console.log('[RobotBridge] Efecto giro izquierda activado');
    } catch (error) {
      console.error('Error activando efecto giro izquierda:', error);
      throw error;
    }
  }

  static async turnOffAllLedEffects(): Promise<void> {
    try {
      await LedModule.turnOffAllEffects();
      console.log('[RobotBridge] Todos los efectos LED apagados');
    } catch (error) {
      console.error('Error apagando efectos LED:', error);
      throw error;
    }
  }

  static async getLedCapabilities(): Promise<any> {
    try {
      const capsJson = await LedModule.getLedCapabilities();
      return JSON.parse(capsJson);
    } catch (error) {
      console.error('Error obteniendo capacidades LED:', error);
      return { hasProZcbLed: false, hasClavicleLight: false };
    }
  }

  static async stopNavigation(): Promise<void> {
    try {
      await RobotSkillModule.executeAction('com.robbie.action.STOP_NAVIGATION', '{}');
      console.log('[RobotBridge] Navegación detenida');
    } catch (error) {
      console.error('Error deteniendo navegación:', error);
      throw error;
    }
  }
}
