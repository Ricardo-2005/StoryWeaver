import { expect, test, type Page, type Route } from '@playwright/test'

const projectId = '22222222-2222-2222-2222-222222222222'
const now = '2026-08-03T00:00:00Z'
const project = { id: projectId, name: '雾港来信', genre: '现代幻想', description: null, authorIntent: null, currentFocus: null, archived: false, version: 0, createdAt: now, updatedAt: now }
function json(route:Route,body:unknown,status=200){return route.fulfill({status,contentType:'application/json',body:JSON.stringify(body)})}

async function mockAssets(page:Page){
  const data:Record<string,Array<Record<string,unknown>>>={characters:[],worldbook:[],outlines:[],chapters:[],skills:[]}
  await page.route('**/api/**',async route=>{const req=route.request(),path=new URL(req.url()).pathname,method=req.method();
    if(path==='/api/auth/login')return json(route,{accessToken:'token',tokenType:'Bearer',expiresAt:now,user:{id:'u',username:'author',email:'a@b.com',createdAt:now}})
    if(path==='/api/me')return json(route,{id:'u',username:'author',email:'a@b.com',createdAt:now})
    if(path==='/api/projects'&&method==='GET')return json(route,[project])
    if(path===`/api/projects/${projectId}`)return json(route,project)
    const body=method==='POST'?req.postDataJSON() as Record<string,unknown>:{}
    if(path===`/api/projects/${projectId}/characters`){if(method==='GET')return json(route,data.characters);const x={id:'c1',projectId,...body,archived:false,version:0,createdAt:now,updatedAt:now,state:{id:'s1',projectId,characterId:'c1',...(body.state as object),version:0,createdAt:now,updatedAt:now}};data.characters=[x];return json(route,x,201)}
    if(path===`/api/projects/${projectId}/worldbook-entries`){if(method==='GET')return json(route,data.worldbook);const x={id:'w1',projectId,...body,embeddingStatus:'NOT_REQUESTED',embeddingModel:null,version:0,createdAt:now,updatedAt:now};data.worldbook=[x];return json(route,x,201)}
    if(path===`/api/projects/${projectId}/outlines`){if(method==='GET')return json(route,data.outlines);const x={id:'o1',projectId,...body,version:0,createdAt:now,updatedAt:now};data.outlines=[x];return json(route,x,201)}
    if(path===`/api/projects/${projectId}/chapters`){if(method==='GET')return json(route,data.chapters);const x={id:'h1',projectId,...body,status:'DRAFT',currentVersionNo:0,version:0,createdAt:now,updatedAt:now,currentVersion:null};data.chapters=[x];return json(route,x,201)}
    if(path===`/api/projects/${projectId}/skills`){if(method==='GET')return json(route,data.skills);const x={id:'k1',projectId,...body,version:0,createdAt:now,updatedAt:now};data.skills=[x];return json(route,x,201)}
    return json(route,{title:'Not Found',status:404},404)
  })
}

async function login(page:Page){await page.goto('/login');await page.getByLabel('邮箱或用户名').fill('author');await page.getByLabel('密码').fill('change-me-123');await page.getByRole('button',{name:'登录'}).click();await page.getByRole('link',{name:'雾港来信'}).first().click()}

test('create Phase 2 assets through real contract-shaped forms',async({page})=>{await mockAssets(page);await login(page)
  await page.getByRole('link',{name:'人物',exact:true}).click();await page.getByRole('button',{name:'创建人物'}).click();await page.getByLabel('姓名').fill('林雾');await page.getByLabel('角色').fill('主角');await page.getByRole('button',{name:'保存',exact:true}).click();await expect(page.getByRole('heading',{name:'林雾'})).toBeVisible()
  await page.getByRole('link',{name:'世界书',exact:true}).click();await page.getByRole('button',{name:'创建条目'}).click();await page.getByRole('button',{name:'保存',exact:true}).click();await expect(page.getByLabel('标题')).toBeFocused();await page.getByLabel('标题').fill('雾港');await page.getByLabel('内容').fill('潮雾覆盖的港城。');await page.getByRole('button',{name:'保存',exact:true}).click();await expect(page.getByRole('heading',{name:'雾港'})).toBeVisible()
  await page.getByRole('link',{name:'大纲',exact:true}).click();await page.getByRole('button',{name:'创建节点'}).click();await page.getByLabel('标题').fill('总纲');await page.getByRole('button',{name:'保存',exact:true}).click();await expect(page.getByRole('treeitem',{name:/总纲/})).toBeVisible();await page.getByRole('treeitem',{name:/总纲/}).press('ArrowDown')
  await page.getByRole('link',{name:'章节',exact:true}).click();await page.getByRole('button',{name:'创建章节'}).click();await page.getByLabel('标题').fill('潮声');await page.getByRole('button',{name:'保存',exact:true}).click();await expect(page.getByRole('heading',{name:'潮声'})).toBeVisible()
  await page.getByRole('link',{name:'Skill',exact:true}).click();await page.getByRole('button',{name:'创建 Skill'}).click();await page.getByLabel('名称').fill('克制叙事');await page.getByRole('button',{name:'保存',exact:true}).click();await expect(page.getByRole('heading',{name:'克制叙事'})).toBeVisible()
})
