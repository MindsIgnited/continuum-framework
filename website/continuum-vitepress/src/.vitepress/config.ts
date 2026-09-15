import { defineConfig } from 'vitepress'

// https://vitepress.vuejs.org/reference/site-config
export default defineConfig({
  title: "Continuum",
  description: "Insanely versatile IoT and Microservice framework",
  base: '/continuum-framework/website/',
  ignoreDeadLinks: [
    // ignore all localhost links
    /^https?:\/\/localhost/,
  ],
  themeConfig: {
    logo: '/icon.png',
    // https://vitepress.vuejs.org/reference/default-theme-config

    nav: nav(),

    sidebar: {
      '/guide/': sidebarGuide(),
      '/reference/': sidebarReference()
    },

    socialLinks: [
      { icon: 'github', link: ' https://github.com/mindsignited/continuum-framework' }
    ],
    footer: {
      message: 'Released under the Apache License.',
      copyright: 'Copyright © 2010-present Mind Ignited'
    }
  }
})

function nav() {
  return [
    { text: 'Guide', link: '/guide/introduction', activeMatch: '/guide/' },
    {
      text: 'Reference',
      link: '/reference/continuum-config',
      activeMatch: '/reference/'
    },
    {
      text: 'Test Status',
      link: 'https://mindsignited.github.io/continuum-framework/allure' // Fully qualified URL
    }
  ]
}

function sidebarGuide() {
  return [
    {
      text: 'Getting Started',
      items: [
        { text: 'Introduction', link: '/guide/introduction' },
        { text: 'Quick Start', link: '/guide/quick-start' }
      ]
    },
    {
      text: 'Core Concepts',
      items: [
        { text: 'Java Services', link: '/guide/java-services' },
        { text: 'Clients', link: '/guide/clients' },
        { text: 'RPC Patterns', link: '/guide/rpc-patterns' },
        // { text: 'Events & Streaming', link: '/guide/events-streaming' } remove until we have a higher level API, this functionality is what RPC is built on so direct usage of this could cause confusion
      ]
    },
    {
      text: 'Configuration & Advanced',
      items: [
        { text: 'Advanced Topics', link: '/guide/advanced' },
        { text: 'Examples', link: '/guide/examples' }
      ]
    }
  ]
}

function sidebarReference() {
  return [
    {
      text: 'Reference',
      items: [
        { text: 'Configuration', link: '/reference/continuum-config' },
        { text: 'C3 IDL', link: '/reference/c3-idl' }
      ]
    },
    {
      text: 'API',
      items: [
        { text: 'Javadoc', link: 'https://www.javadoc.io/doc/org.mindsignited/continuum-core' }
      ]
    }
  ]
}
