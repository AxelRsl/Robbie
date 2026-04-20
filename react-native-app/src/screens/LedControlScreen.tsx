import React, { useState, useEffect } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, Alert } from 'react-native';
import { LedHelper } from '@/utils/LedHelper';
import { RobotBridge } from '@/services/RobotBridge';

export const LedControlScreen: React.FC = () => {
  const [currentStatus, setCurrentStatus] = useState<any>(null);
  const [isLoading, setIsLoading] = useState(false);

  useEffect(() => {
    // Establecer color Ikalp por defecto al cargar la pantalla
    handleSetIkalpPrimary();
    loadLedStatus();
  }, []);

  const loadLedStatus = async () => {
    try {
      const status = await RobotBridge.getLedStatus();
      setCurrentStatus(status);
    } catch (error) {
      console.error('Error cargando estado LED:', error);
    }
  };

  const handleSetIkalpPrimary = async () => {
    setIsLoading(true);
    try {
      await LedHelper.setIkalpPrimary();
      await loadLedStatus();
      Alert.alert('Éxito', 'Color Ikalp principal establecido');
    } catch (error) {
      Alert.alert('Error', 'No se pudo establecer el color Ikalp');
    } finally {
      setIsLoading(false);
    }
  };

  const handleSetIkalpDark = async () => {
    setIsLoading(true);
    try {
      await LedHelper.setIkalpDark();
      await loadLedStatus();
      Alert.alert('Éxito', 'Color Ikalp oscuro establecido');
    } catch (error) {
      Alert.alert('Error', 'No se pudo establecer el color Ikalp oscuro');
    } finally {
      setIsLoading(false);
    }
  };

  const handleSetIkalpAccent = async () => {
    setIsLoading(true);
    try {
      await LedHelper.setIkalpAccent();
      await loadLedStatus();
      Alert.alert('Éxito', 'Color Ikalp accent establecido');
    } catch (error) {
      Alert.alert('Error', 'No se pudo establecer el color Ikalp accent');
    } finally {
      setIsLoading(false);
    }
  };

  const handleRestoreDefault = async () => {
    setIsLoading(true);
    try {
      await LedHelper.restoreDefault();
      await loadLedStatus();
      Alert.alert('Éxito', 'Color por defecto restaurado');
    } catch (error) {
      Alert.alert('Error', 'No se pudo restaurar el color por defecto');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <View style={styles.container}>
      <Text style={styles.title}>Control LEDs Ikalp</Text>
      
      {currentStatus && (
        <View style={styles.statusContainer}>
          <Text style={styles.statusTitle}>Estado Actual:</Text>
          <Text style={styles.statusText}>Color: {currentStatus.currentColor}</Text>
          <Text style={styles.statusText}>Efecto: {currentStatus.currentEffect}</Text>
          <Text style={styles.statusText}>Brillo: {currentStatus.brightness}%</Text>
        </View>
      )}

      <View style={styles.buttonContainer}>
        <TouchableOpacity 
          style={[styles.button, styles.primaryButton]} 
          onPress={handleSetIkalpPrimary}
          disabled={isLoading}
        >
          <Text style={styles.buttonText}>Ikalp Principal</Text>
          <Text style={styles.colorCode}>#E4027C</Text>
        </TouchableOpacity>

        <TouchableOpacity 
          style={[styles.button, styles.darkButton]} 
          onPress={handleSetIkalpDark}
          disabled={isLoading}
        >
          <Text style={styles.buttonText}>Ikalp Oscuro</Text>
          <Text style={styles.colorCode}>#A80059</Text>
        </TouchableOpacity>

        <TouchableOpacity 
          style={[styles.button, styles.accentButton]} 
          onPress={handleSetIkalpAccent}
          disabled={isLoading}
        >
          <Text style={styles.buttonText}>Ikalp Accent</Text>
          <Text style={styles.colorCode}>#00BFA5</Text>
        </TouchableOpacity>

        <TouchableOpacity 
          style={[styles.button, styles.defaultButton]} 
          onPress={handleRestoreDefault}
          disabled={isLoading}
        >
          <Text style={styles.buttonText}>Restaurar Por Defecto</Text>
        </TouchableOpacity>
      </View>

      <TouchableOpacity 
        style={styles.refreshButton} 
        onPress={loadLedStatus}
        disabled={isLoading}
      >
        <Text style={styles.refreshText}>Actualizar Estado</Text>
      </TouchableOpacity>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 20,
    backgroundColor: '#f5f5f5',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
    textAlign: 'center',
    marginBottom: 20,
    color: '#E4027C',
  },
  statusContainer: {
    backgroundColor: 'white',
    padding: 15,
    borderRadius: 10,
    marginBottom: 20,
    elevation: 2,
  },
  statusTitle: {
    fontSize: 16,
    fontWeight: 'bold',
    marginBottom: 10,
    color: '#333',
  },
  statusText: {
    fontSize: 14,
    color: '#666',
    marginBottom: 5,
  },
  buttonContainer: {
    flex: 1,
    justifyContent: 'center',
  },
  button: {
    padding: 15,
    borderRadius: 10,
    marginBottom: 15,
    alignItems: 'center',
    elevation: 2,
  },
  primaryButton: {
    backgroundColor: '#E4027C',
  },
  darkButton: {
    backgroundColor: '#A80059',
  },
  accentButton: {
    backgroundColor: '#00BFA5',
  },
  defaultButton: {
    backgroundColor: '#666',
  },
  buttonText: {
    color: 'white',
    fontSize: 16,
    fontWeight: 'bold',
  },
  colorCode: {
    color: 'white',
    fontSize: 12,
    marginTop: 5,
    opacity: 0.8,
  },
  refreshButton: {
    backgroundColor: '#333',
    padding: 12,
    borderRadius: 8,
    alignItems: 'center',
  },
  refreshText: {
    color: 'white',
    fontSize: 14,
  },
});
