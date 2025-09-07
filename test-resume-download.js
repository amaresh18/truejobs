// Test script to debug resume download issue
const axios = require('axios');

async function testResumeDownload() {
  try {
    // First, get a valid JWT token by logging in
    console.log('1. Attempting to login...');
    const loginResponse = await axios.post('http://localhost:8081/api/auth/login', {
      email: 'candidate@example.com',
      password: 'password123'
    });

    const token = loginResponse.data.token;
    console.log('✓ Login successful, token received');

    // Get user's resumes
    console.log('2. Getting user resumes...');
    const resumesResponse = await axios.get('http://localhost:8081/api/resumes', {
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });

    const resumes = resumesResponse.data;
    console.log(`✓ Found ${resumes.length} resumes:`, resumes.map(r => ({ id: r.id, title: r.title, filePath: r.filePath })));

    if (resumes.length === 0) {
      console.log('No resumes found to test download');
      return;
    }

    // Try to download the first resume
    const resumeToDownload = resumes[0];
    console.log(`3. Attempting to download resume ID: ${resumeToDownload.id}, title: ${resumeToDownload.title}`);
    
    const downloadResponse = await axios.get(`http://localhost:8081/api/resumes/${resumeToDownload.id}/download`, {
      headers: {
        'Authorization': `Bearer ${token}`
      },
      responseType: 'blob'
    });

    console.log('✓ Download successful!');
    console.log('Response headers:', downloadResponse.headers);
    console.log('Response size:', downloadResponse.data.size || downloadResponse.data.length);

  } catch (error) {
    console.error('❌ Error occurred:');
    console.error('Status:', error.response?.status);
    console.error('Status Text:', error.response?.statusText);
    console.error('Response Data:', error.response?.data);
    console.error('Error Message:', error.message);
    console.error('Full Error:', error);
  }
}

testResumeDownload();
