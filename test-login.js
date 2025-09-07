// Simple login test script
const axios = require('axios');
const API_BASE_URL = process.env.API_BASE_URL || 'http://localhost:8081/api';

async function testLogin() {
  try {
    console.log('Testing job seeker login...');
    
    // Test login
    const loginResponse = await axios.post(`${API_BASE_URL}/auth/login`, {
      email: 'candidate@example.com',
      password: 'password123'
    });
    
    console.log('Login successful!');
    console.log('User:', loginResponse.data.user);
    console.log('Token:', loginResponse.data.token.substring(0, 50) + '...');
    
    // Test /me endpoint with the token
    const meResponse = await axios.get(`${API_BASE_URL}/auth/me`, {
      headers: {
        'Authorization': `Bearer ${loginResponse.data.token}`
      }
    });
    
    console.log('Me endpoint successful!');
    console.log('Me data:', meResponse.data);
    
    console.log('\nAll tests passed! Job seeker login is working correctly.');
    
  } catch (error) {
    console.error('Login test failed:', error.response?.data || error.message);
  }
}

testLogin();
