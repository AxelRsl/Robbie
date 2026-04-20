import React, { useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
} from 'react-native';
import { useAppStore } from '@/stores/useAppStore';
import { RobotBridge } from '@/services/RobotBridge';

export const NavigationScreen: React.FC = () => {
  const { navigation, setNavigation, setCurrentMode } = useAppStore();

  useEffect(() => {
    if (!navigation) {
      return;
    }

    // Simular llegada al destino después de 5 segundos
    const arrivalTimer = setTimeout(async () => {
      await RobotBridge.say(`He llegado a ${navigation.destination}`);
      setNavigation(null);
      setCurrentMode('retail');
    }, 5000);

    return () => {
      clearTimeout(arrivalTimer);
    };
  }, [navigation, setNavigation, setCurrentMode]);

  if (!navigation) {
    return null;
  }

  const handleStopNavigation = async () => {
    // Llamar al metodo nativo para detener navegacion
    try {
      await RobotBridge.stopNavigation();
    } catch (error) {
      console.error('Error deteniendo navegacion:', error);
    }
    setNavigation(null);
    setCurrentMode('menu');
  };

  return (
    <View style={styles.container}>
      <View style={styles.content}>
        <Text style={styles.title}>Navegando hacia:</Text>
        <Text style={styles.destination}>{navigation.destination}</Text>

        <TouchableOpacity
          style={styles.stopButton}
          onPress={handleStopNavigation}
          activeOpacity={0.7}
        >
          <Text style={styles.stopButtonText}>Detener Navegación</Text>
        </TouchableOpacity>

        <Text style={styles.voiceCommand}>
          Comando de voz: "Detener" o "Parar"
        </Text>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#F5F5F5',
    justifyContent: 'center',
    alignItems: 'center',
  },
  content: {
    alignItems: 'center',
    padding: 24,
  },
  title: {
    fontSize: 20,
    fontWeight: '600',
    color: '#757575',
    marginBottom: 8,
  },
  destination: {
    fontSize: 28,
    fontWeight: 'bold',
    color: '#212121',
    marginBottom: 48,
    textAlign: 'center',
  },
  stopButton: {
    backgroundColor: '#E4027C',
    paddingHorizontal: 32,
    paddingVertical: 16,
    borderRadius: 12,
    marginBottom: 16,
    elevation: 3,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.2,
    shadowRadius: 4,
  },
  stopButtonText: {
    color: '#FFFFFF',
    fontSize: 18,
    fontWeight: 'bold',
  },
  voiceCommand: {
    fontSize: 14,
    color: '#9E9E9E',
    fontStyle: 'italic',
  },
});
