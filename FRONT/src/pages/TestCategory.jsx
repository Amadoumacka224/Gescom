import { useState } from 'react';
import api from '../services/api';
import { toast, Toaster } from 'react-hot-toast';

const TestCategory = () => {
  const [result, setResult] = useState('');
  const [loading, setLoading] = useState(false);

  const testCreate = async () => {
    setLoading(true);
    setResult('Test en cours...');

    try {
      console.log('=== TEST CATEGORY CREATION ===');

      // Test 1: Vérifier le token
      const token = localStorage.getItem('token');
      const user = localStorage.getItem('user');
      console.log('Token:', token ? token.substring(0, 50) + '...' : 'NONE');
      console.log('User:', user);

      // Test 2: Créer une catégorie
      const categoryData = {
        name: 'Test Category ' + Date.now(),
        code: 'TEST' + Date.now(),
        description: 'Test automatique',
        active: true
      };

      console.log('Sending:', categoryData);

      const response = await api.post('/categories', categoryData);

      console.log('Success response:', response);

      setResult(`✅ SUCCÈS!\n\n${JSON.stringify(response.data, null, 2)}`);
      toast.success('Catégorie créée avec succès!');

    } catch (error) {
      console.error('=== ERROR ===', error);

      let errorMsg = '❌ ERREUR\n\n';
      errorMsg += `Message: ${error.message}\n`;
      errorMsg += `Status: ${error.response?.status}\n`;
      errorMsg += `Data: ${JSON.stringify(error.response?.data, null, 2)}\n`;
      errorMsg += `URL: ${error.config?.url}\n`;
      errorMsg += `Method: ${error.config?.method}\n`;
      errorMsg += `Headers: ${JSON.stringify(error.config?.headers, null, 2)}`;

      setResult(errorMsg);
      toast.error(error.response?.data?.error || error.message);
    } finally {
      setLoading(false);
    }
  };

  const testList = async () => {
    setLoading(true);
    try {
      const response = await api.get('/categories');
      setResult(`✅ ${response.data.length} catégories:\n\n${JSON.stringify(response.data, null, 2)}`);
      toast.success(`${response.data.length} catégories chargées`);
    } catch (error) {
      setResult(`❌ Erreur: ${error.message}\n${JSON.stringify(error.response?.data, null, 2)}`);
      toast.error(error.message);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="p-8 max-w-4xl mx-auto">
      <Toaster position="top-right" />

      <h1 className="text-3xl font-bold mb-6">Test Création de Catégories</h1>

      <div className="space-y-4 mb-6">
        <div className="flex gap-4">
          <button
            onClick={testCreate}
            disabled={loading}
            className="px-6 py-3 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50"
          >
            {loading ? 'Test en cours...' : '🧪 Créer une catégorie de test'}
          </button>

          <button
            onClick={testList}
            disabled={loading}
            className="px-6 py-3 bg-green-600 text-white rounded-lg hover:bg-green-700 disabled:opacity-50"
          >
            📋 Lister les catégories
          </button>
        </div>

        <div className="p-4 bg-yellow-50 border border-yellow-200 rounded-lg">
          <p className="text-sm">
            <strong>Note:</strong> Assurez-vous d'être connecté en tant qu'ADMIN pour créer des catégories.
            <br />
            Ouvrez la console (F12) pour voir les logs détaillés.
          </p>
        </div>
      </div>

      {result && (
        <div className="p-6 bg-gray-100 rounded-lg border border-gray-300">
          <h2 className="font-bold mb-2">Résultat:</h2>
          <pre className="whitespace-pre-wrap text-sm">{result}</pre>
        </div>
      )}
    </div>
  );
};

export default TestCategory;
