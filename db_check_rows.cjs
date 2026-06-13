const mysql = require('mysql2/promise');

async function run() {
  const connection = await mysql.createConnection({
    host: 'localhost',
    port: 3306,
    user: 'root',
    password: 'Dhipak#2006#',
    database: 'connectit_db'
  });

  try {
    const [userRows] = await connection.execute("SELECT * FROM users WHERE email='admin@technosprint.net'");
    console.log('User rows for admin@technosprint.net:', JSON.stringify(userRows, null, 2));

    const [settingsRows] = await connection.execute("SELECT * FROM system_settings");
    console.log('System settings rows:', JSON.stringify(settingsRows, null, 2));
  } catch (error) {
    console.error('Error:', error.message);
  } finally {
    await connection.end();
  }
}

run();
