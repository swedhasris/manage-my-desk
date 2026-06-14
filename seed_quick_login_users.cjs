const mysql = require('mysql2/promise');

// ── Password hashes (simpleHash algorithm) ───────────────────────────────────
// simpleHash('Poland@01')   = 'h_ps1kdz_9'
// simpleHash('123202')      = 'h_nzmtky_6'
// simpleHash('Password123!')= 'h_c2sm7e_12'

const usersToSeed = [
  // ── Ultra Super Admins ──────────────────────────────────────────────────────
  {
    uid: 'demo_t342dq',
    email: 'arun.g@technosprint.net',
    name: 'Arun G (Ultra Super Admin)',
    role: 'ultra_super_admin',
    password_hash: 'h_ps1kdz_9'   // password: Poland@01
  },
  {
    uid: 'demo_swedha',
    email: 'swedhasris@gmail.com',
    name: 'Swedha (Ultra Super Admin)',
    role: 'ultra_super_admin',
    password_hash: 'h_nzmtky_6'   // password: 123202
  },
  // ── Other Demo Roles ────────────────────────────────────────────────────────
  {
    uid: 'demo_voust',
    email: 'ulter@technosprint.net',
    name: 'Demo Super Admin',
    role: 'super_admin',
    password_hash: 'h_c2sm7e_12'  // password: Password123!
  },
  {
    uid: 'demo_admin',
    email: 'admin@technosprint.net',
    name: 'Demo Admin',
    role: 'admin',
    password_hash: 'h_c2sm7e_12'  // password: Password123!
  },
  {
    uid: 'demo_agent',
    email: 'agent@technosprint.net',
    name: 'Demo Support Agent',
    role: 'agent',
    password_hash: 'h_c2sm7e_12'  // password: Password123!
  },
  {
    uid: 'demo_user',
    email: 'user@technosprint.net',
    name: 'Demo User',
    role: 'user',
    password_hash: 'h_c2sm7e_12'  // password: Password123!
  }
];

async function run() {
  // ── Connection config ──────────────────────────────────────────────────────
  // For local MySQL: host=localhost, port=3306 (or 3307), password=''
  // For Render hosted DB: set MYSQL_HOST, MYSQL_PORT, MYSQL_USER, MYSQL_PASSWORD, MYSQL_DATABASE env vars
  const host     = process.env.MYSQL_HOST     || 'localhost';
  const port     = parseInt(process.env.MYSQL_PORT || '3306');
  const user     = process.env.MYSQL_USER     || 'root';
  const password = process.env.MYSQL_PASSWORD || '';
  const database = process.env.MYSQL_DATABASE || 'connectit_db';

  console.log(`Connecting to MySQL at ${host}:${port} db=${database}...`);
  const connection = await mysql.createConnection({ host, port, user, password, database });

  console.log('Seeding ultra super admin and demo users...');
  for (const u of usersToSeed) {
    await connection.execute(`
      INSERT INTO users (uid, email, name, role, password_hash, is_active)
      VALUES (?, ?, ?, ?, ?, 1)
      ON DUPLICATE KEY UPDATE
        name          = VALUES(name),
        role          = VALUES(role),
        password_hash = VALUES(password_hash),
        is_active     = 1
    `, [u.uid, u.email, u.name, u.role, u.password_hash]);
    console.log(`  ✅ Seeded/updated: ${u.email} [${u.role}]`);
  }

  await connection.end();
  console.log('\nDone! Users seeded successfully.');
  console.log('\nLogin credentials:');
  console.log('  arun.g@technosprint.net  / Poland@01    (ultra_super_admin)');
  console.log('  swedhasris@gmail.com     / 123202       (ultra_super_admin)');
  console.log('  admin@technosprint.net   / Password123! (admin)');
}

run().catch(console.error);

