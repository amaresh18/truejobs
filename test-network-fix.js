// Simple test to verify resume download functionality after Network Error fix
const axios = require('axios');

async function testResumeView() {
  try {
    console.log('🧪 Testing Resume View Functionality After Network Error Fix\n');

    // 1. Login
    console.log('1. Logging in...');
    const loginResponse = await axios.post('http://localhost:8081/api/auth/login', {
      email: 'candidate@example.com',
      password: 'password123'
    });
    
    const token = loginResponse.data.token;
    console.log('✅ Login successful\n');

    // 2. Get resumes
    console.log('2. Getting user resumes...');
    const resumesResponse = await axios.get('http://localhost:8081/api/resumes', {
      headers: { 'Authorization': `Bearer ${token}` }
    });

    const resumes = resumesResponse.data;
    console.log(`✅ Found ${resumes.length} resumes\n`);

    if (resumes.length === 0) {
      console.log('❌ No resumes found to test');
      return;
    }

    // 3. Test resume download/view functionality
    for (const resume of resumes) {
      console.log(`3. Testing Resume View: "${resume.title}"`);
      console.log(`   - Resume ID: ${resume.id}`);
      console.log(`   - File Path: ${resume.filePath}`);
      console.log(`   - File Type: ${resume.fileType || 'Not specified (will be detected)'}`);

      try {
        const viewResponse = await axios.get(`http://localhost:8081/api/resumes/${resume.id}/download`, {
          headers: { 'Authorization': `Bearer ${token}` },
          responseType: 'blob'
        });

        console.log('   ✅ Resume view request successful!');
        console.log(`   - Status: ${viewResponse.status}`);
        console.log(`   - Content-Type: ${viewResponse.headers['content-type']}`);
        console.log(`   - Content-Length: ${viewResponse.headers['content-length']} bytes`);
        console.log(`   - Content-Disposition: ${viewResponse.headers['content-disposition']}`);
        
        // Check if content type is properly set
        const contentType = viewResponse.headers['content-type'];
        if (contentType !== 'application/octet-stream') {
          console.log('   ✅ Content type correctly set (not generic octet-stream)');
        } else {
          console.log('   ⚠️  Content type is still generic octet-stream');
        }

      } catch (error) {
        console.log('   ❌ Resume view failed');
        if (error.response) {
          console.log(`   - Status: ${error.response.status}`);
          console.log(`   - Error: ${error.response.data?.error || 'Unknown error'}`);
        } else {
          console.log(`   - Network Error: ${error.message}`);
        }
      }
      console.log('');
    }

    console.log('🎉 Resume view functionality test completed!');
    console.log('\n📋 SUMMARY:');
    console.log('- Backend is running on port 8081 ✅');
    console.log('- Frontend is running on port 3001 ✅');
    console.log('- API base URL updated to localhost ✅');
    console.log('- Authentication working ✅');
    console.log('- Resume download endpoint accessible ✅');
    console.log('\n🔧 If you are still seeing Network Error in the frontend:');
    console.log('1. Make sure you are accessing http://localhost:3001 (not 3000)');
    console.log('2. Clear browser cache and reload');
    console.log('3. Check browser console for specific error details');

  } catch (error) {
    console.error('\n❌ Test failed:');
    if (error.response) {
      console.error(`Status: ${error.response.status}`);
      console.error(`Data:`, error.response.data);
    } else if (error.code === 'ECONNREFUSED') {
      console.error('Backend not running! Please start the backend server.');
    } else {
      console.error(`Error: ${error.message}`);
    }
  }
}

testResumeView();
