// Test script to verify file type preservation in downloads
const axios = require('axios');

async function testFileTypes() {
  try {
    // Login first
    console.log('1. Logging in...');
    const loginResponse = await axios.post('http://localhost:8081/api/auth/login', {
      email: 'candidate@example.com',
      password: 'password123'
    });

    const token = loginResponse.data.token;
    console.log('✓ Login successful');

    // Get resumes
    console.log('2. Getting resumes...');
    const resumesResponse = await axios.get('http://localhost:8081/api/resumes', {
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });

    const resumes = resumesResponse.data;
    console.log(`✓ Found ${resumes.length} resumes`);

    // Test download for each resume
    for (const resume of resumes) {
      console.log(`\n3. Testing download for resume: ${resume.title}`);
      console.log(`   File path: ${resume.filePath}`);
      console.log(`   File type stored: ${resume.fileType || 'N/A'}`);
      
      const downloadResponse = await axios.get(`http://localhost:8081/api/resumes/${resume.id}/download`, {
        headers: {
          'Authorization': `Bearer ${token}`
        },
        responseType: 'blob'
      });

      console.log(`   Response Content-Type: ${downloadResponse.headers['content-type']}`);
      console.log(`   Content-Disposition: ${downloadResponse.headers['content-disposition']}`);
      
      // Check if the content type matches the file extension
      const fileExtension = resume.filePath.split('.').pop().toLowerCase();
      const contentType = downloadResponse.headers['content-type'];
      
      console.log(`   File extension: .${fileExtension}`);
      
      // Verify content type matches file extension
      let expectedContentType;
      switch (fileExtension) {
        case 'pdf':
          expectedContentType = 'application/pdf';
          break;
        case 'doc':
          expectedContentType = 'application/msword';
          break;
        case 'docx':
          expectedContentType = 'application/vnd.openxmlformats-officedocument.wordprocessingml.document';
          break;
        default:
          expectedContentType = 'application/octet-stream';
      }
      
      if (contentType === expectedContentType) {
        console.log(`   ✓ Content-Type matches expected: ${expectedContentType}`);
      } else {
        console.log(`   ⚠️  Content-Type mismatch. Expected: ${expectedContentType}, Got: ${contentType}`);
      }
    }

    console.log('\n✓ File type testing completed!');

  } catch (error) {
    console.error('❌ Error occurred:');
    if (error.response) {
      console.error('Status:', error.response.status);
      console.error('Response Data:', error.response.data);
    } else {
      console.error('Error Message:', error.message);
    }
  }
}

testFileTypes();
