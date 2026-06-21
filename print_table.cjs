require('dotenv').config();
const mysql = require('mysql2/promise');

async function run() {
  const connection = await mysql.createConnection({
    host: process.env.MYSQL_HOST || 'localhost',
    port: parseInt(process.env.MYSQL_PORT) || 3306,
    user: process.env.MYSQL_USER || 'root',
    password: process.env.MYSQL_PASSWORD || '',
    database: process.env.MYSQL_DATABASE || 'connectit_db'
  });

  const tableName = process.argv[2];

  try {
    if (!tableName) {
      // List all tables and row counts
      const [tables] = await connection.query('SHOW TABLES');
      const tableList = tables.map(r => Object.values(r)[0]);
      
      console.log('\n--- MySQL Tables & Row Counts ---');
      const summary = [];
      for (const name of tableList) {
        const [[countResult]] = await connection.query(`SELECT COUNT(*) as count FROM \`${name}\``);
        summary.push({ Table: name, 'Row Count': countResult.count });
      }
      console.table(summary);
      console.log('\nTip: Run `node print_table.cjs <table_name>` to view records from a specific table.\n');
    } else {
      // Query specific table rows
      const [rows] = await connection.query(`SELECT * FROM \`${tableName}\` LIMIT 50`);
      console.log(`\n--- Records for Table: ${tableName} (Limit 50) ---`);
      if (rows.length === 0) {
        console.log('No records found in this table.');
      } else {
        console.table(rows);
      }
      console.log('\n');
    }
  } catch (error) {
    console.error('Error running query:', error.message);
  } finally {
    await connection.end();
  }
}

run();
