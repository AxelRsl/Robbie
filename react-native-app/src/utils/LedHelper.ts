import { RobotBridge } from '@/services/RobotBridge';

/**
 * Helper para control de LEDs con colores Ikalp
 */
export class LedHelper {
  
  // Paleta de colores Ikalp
  static readonly IKALP_COLORS = {
    PRIMARY: '#E4027C',    // Rosa principal Ikalp
    DARK: '#A80059',       // Rosa oscuro
    ACCENT: '#00BFA5',     // Verde agua (accent)
    WHITE: '#FFFFFF',
    BLACK: '#000000',
  };

  /**
   * Establece color sólido Ikalp principal
   */
  static async setIkalpPrimary(): Promise<void> {
    try {
      await RobotBridge.setLedColor(this.IKALP_COLORS.PRIMARY);
      console.log('[LedHelper] Color Ikalp principal establecido');
    } catch (error) {
      console.error('[LedHelper] Error estableciendo color Ikalp:', error);
      throw error;
    }
  }

  /**
   * Establece color sólido Ikalp oscuro
   */
  static async setIkalpDark(): Promise<void> {
    try {
      await RobotBridge.setLedColor(this.IKALP_COLORS.DARK);
      console.log('[LedHelper] Color Ikalp oscuro establecido');
    } catch (error) {
      console.error('[LedHelper] Error estableciendo color Ikalp oscuro:', error);
      throw error;
    }
  }

  /**
   * Establece color sólido accent Ikalp
   */
  static async setIkalpAccent(): Promise<void> {
    try {
      await RobotBridge.setLedColor(this.IKALP_COLORS.ACCENT);
      console.log('[LedHelper] Color Ikalp accent establecido');
    } catch (error) {
      console.error('[LedHelper] Error estableciendo color Ikalp accent:', error);
      throw error;
    }
  }

  /**
   * Restaura al color por defecto (Ikalp principal)
   */
  static async restoreDefault(): Promise<void> {
    try {
      await RobotBridge.restoreLedDefault();
      console.log('[LedHelper] Color por defecto restaurado');
    } catch (error) {
      console.error('[LedHelper] Error restaurando color por defecto:', error);
      throw error;
    }
  }

  /**
   * Establece cualquier color sólido de la paleta Ikalp
   */
  static async setIkalpColor(colorName: keyof typeof LedHelper.IKALP_COLORS): Promise<void> {
    try {
      const color = this.IKALP_COLORS[colorName];
      await RobotBridge.setLedColor(color);
      console.log(`[LedHelper] Color Ikalp ${colorName} establecido: ${color}`);
    } catch (error) {
      console.error(`[LedHelper] Error estableciendo color Ikalp ${colorName}:`, error);
      throw error;
    }
  }
}
