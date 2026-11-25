const fs = require('fs')
const path = require('path')
const puppeteer = require('D:/indoor-plant-data/shot/node_modules/puppeteer-core')

const EDGE = 'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
const ROOT = 'C:\\Users\\lenovo\\greenthumb'
const OUT = path.join(ROOT, 'report', 'weekly_shots')
const API = 'http://127.0.0.1:8081/api/v1'
const APP = 'http://127.0.0.1:5174'
const cases = JSON.parse(
  fs.readFileSync(path.join(ROOT, 'report', 'weekly_cases.json'), 'utf8')
)

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

async function api(method, endpoint, body, token) {
  const response = await fetch(API + endpoint, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: body ? JSON.stringify(body) : undefined
  })
  return response.json()
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true })

  const username = `weekly${Date.now() % 1000000}`
  const registration = await api('POST', '/auth/register', {
    username,
    password: process.env.SHOT_PASSWORD || `weekly-${Date.now()}`,
    nickname: '本周汇报'
  })
  if (registration.code !== 0) {
    throw new Error(`register failed: ${JSON.stringify(registration)}`)
  }
  const token = registration.data.token

  const speciesResponse = await api('GET', '/species?size=200', null, token)
  const speciesList = speciesResponse.data.list || []
  const speciesIdByName = Object.fromEntries(
    speciesList.map((item) => [item.commonName, item.id])
  )

  for (const item of cases) {
    const speciesId = speciesIdByName[item.species]
    if (!speciesId) throw new Error(`species missing: ${item.species}`)
    const plant = await api(
      'POST',
      '/my-plants',
      {
        speciesId,
        nickname: item.species,
        location: '阳台',
        lightLevel: 'BRIGHT_INDIRECT',
        potSize: 'MEDIUM',
        potMaterial: 'CERAMIC',
        soilType: 'PEAT'
      },
      token
    )
    if (plant.code !== 0) {
      throw new Error(`create plant failed: ${JSON.stringify(plant)}`)
    }
  }

  const browser = await puppeteer.launch({
    executablePath: EDGE,
    headless: 'new',
    args: ['--no-sandbox', '--disable-gpu', '--window-size=1440,1000']
  })
  const page = await browser.newPage()
  await page.setViewport({
    width: 1440,
    height: 1000,
    deviceScaleFactor: 2.5
  })

  async function go(url, wait = 2500) {
    try {
      await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 30000 })
    } catch {
      // The SPA may finish navigation after the initial timeout; the wait below
      // still gives Vue time to render the diagnosis page.
    }
    await sleep(wait)
  }

  async function clickButton(label) {
    return page.evaluate((text) => {
      const buttons = [...document.querySelectorAll('button')]
      let target = buttons.find(
        (button) => button.innerText.replace(/\s/g, '') === text
      )
      if (!target) {
        target = buttons.find(
          (button) =>
            button.innerText.replace(/\s/g, '').includes(text) && !button.disabled
        )
      }
      if (!target) return false
      target.scrollIntoView({ block: 'center' })
      target.click()
      return true
    }, label)
  }

  async function upload(file) {
    const input = await page.$('input[type=file]')
    if (!input) return false
    await input.uploadFile(file)
    await sleep(1800)
    return true
  }

  async function selectPlant(name) {
    await page.evaluate(() => document.querySelector('.el-select')?.click())
    await sleep(700)
    const selected = await page.evaluate((plantName) => {
      const item = [...document.querySelectorAll('.el-select-dropdown__item')].find(
        (node) => (node.innerText || '').includes(plantName)
      )
      if (!item) return false
      item.click()
      return true
    }, name)
    await sleep(700)
    return selected
  }

  async function pickRecognizedSpecies(name) {
    return page.evaluate((speciesName) => {
      const candidates = [...document.querySelectorAll('.recog-item')]
      const match = candidates.find((node) =>
        (node.innerText || '').includes(speciesName)
      )
      if (!match) return false
      match.click()
      return true
    }, name)
  }

  async function pageText() {
    return page.evaluate(
      () => document.querySelector('.app-main')?.innerText || document.body.innerText
    )
  }

  await go(`${APP}/login`, 1800)
  await page.evaluate(
    (authToken, user) => {
      localStorage.setItem('gt_token', authToken)
      localStorage.setItem('gt_user', JSON.stringify(user))
    },
    token,
    { ...registration.data.user, nickname: '本周汇报' }
  )

  const report = []
  for (const item of cases) {
    console.log(`\n===== ${item.label} =====`)
    await go(`${APP}/diagnose`, 2800)
    await clickButton('重新开始')
    await sleep(900)

    // Select an existing plant first so the disease request always carries the
    // intended species, even if third-party species recognition is unavailable.
    await clickButton('下一步')
    await sleep(700)
    await clickButton('下一步')
    await sleep(700)
    const plantSelected = await selectPlant(item.species)
    await clickButton('上一步')
    await sleep(600)
    await clickButton('上一步')
    await sleep(600)

    const uploaded = await upload(item.image)
    await clickButton('开始识别')
    await sleep(5500)
    const candidatePicked = await pickRecognizedSpecies(item.species)
    await sleep(600)

    await clickButton('下一步')
    await sleep(1200)
    const diseaseClicked = await clickButton('开始病害识别')
    await sleep(7000)

    const screenshotPath = path.join(OUT, `${item.out}.png`)
    await page.screenshot({ path: screenshotPath, fullPage: false })
    const body = await pageText()
    const start = body.indexOf('第 2 步')
    const excerpt = body.slice(start, start + 520).replace(/\n+/g, ' / ')
    report.push({
      label: item.label,
      plantSelected,
      uploaded,
      candidatePicked,
      diseaseClicked,
      screenshotPath,
      excerpt
    })
    console.log(`  screenshot=${screenshotPath}`)
    console.log(`  excerpt=${excerpt.slice(0, 320)}`)
  }

  await browser.close()
  fs.writeFileSync(
    path.join(OUT, 'report.json'),
    JSON.stringify(report, null, 2),
    'utf8'
  )
  console.log('\nDONE')
})().catch((error) => {
  console.error('ERR', error.stack || error.message)
  process.exit(1)
})
