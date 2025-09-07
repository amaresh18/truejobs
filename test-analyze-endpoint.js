const axios = require('axios');

async function testAnalyzeEndpoint() {
  console.log('Testing resume analyze endpoint...');
  
  try {
    // First, test if backend is running
    console.log('1. Testing backend health...');
    const healthResponse = await axios.get('http://localhost:8081/actuator/health');
    console.log('✓ Backend is running');
    
    // Test the analyze endpoint
    console.log('2. Testing analyze endpoint...');
    const token = 'eyJhbGciOiJIUzM4NCJ9.eyJzdWIiOiJnLmFtYXJlc2gxOEBnbWFpbC5jb20iLCJ1c2VySWQiOjMsInJvbGUiOiJDQU5ESURBVEUiLCJpYXQiOjE3NTU0MzA5MjMsImV4cCI6MTc1NTUxNzMyM30.H8YJD2o-KVECwquCP_bLIRW8Lsg9slO5QoFt5sIoYPqmE3zmYZCOHeKje1Sbdfg9';
    
    const analyzeResponse = await axios.post('http://localhost:8081/api/resumes/analyze', {
      resumeId: 2,
      jobId: 1
    }, {
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    });
    
    console.log('✓ Analyze endpoint successful!');
    console.log('Response:', analyzeResponse.data);
    
  } catch (error) {
    console.error('❌ Error occurred:');
    if (error.response) {
      console.error('Status:', error.response.status);
      console.error('Status Text:', error.response.statusText);
      console.error('Response Data:', error.response.data);
    } else {
      console.error('Error Message:', error.message);
    }
  }
}

testAnalyzeEndpoint();
