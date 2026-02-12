#!/usr/bin/env python3
"""
文档引用检查工具
检查文档中的内部链接是否有效
"""

import os
import re
import sys
from pathlib import Path
from typing import List, Dict, Tuple

# ANSI 颜色代码
RED = '\033[0;31m'
GREEN = '\033[0;32m'
YELLOW = '\033[1;33m'
BLUE = '\033[0;34m'
NC = '\033[0m'  # No Color


def print_header(text: str):
    """打印标题"""
    print(f"\n{BLUE}{'=' * 60}{NC}")
    print(f"{BLUE}{text}{NC}")
    print(f"{BLUE}{'=' * 60}{NC}\n")


def find_markdown_files(root_dir: str) -> List[Path]:
    """查找所有 Markdown 文件"""
    md_files = []
    for path in Path(root_dir).rglob('*.md'):
        # 跳过 node_modules 和隐藏目录
        if 'node_modules' in str(path) or '/.git/' in str(path):
            continue
        md_files.append(path)
    return md_files


def extract_links(content: str, file_path: Path) -> List[Tuple[str, int]]:
    """提取文档中的所有内部链接"""
    links = []
    
    # 匹配 [text](link) 格式的链接
    pattern = r'\[([^\]]+)\]\(([^\)]+)\)'
    
    for line_num, line in enumerate(content.split('\n'), 1):
        matches = re.finditer(pattern, line)
        for match in matches:
            link = match.group(2)
            
            # 只检查内部链接（相对路径的 .md 文件）
            if link.endswith('.md') and not link.startswith('http'):
                # 移除锚点
                link_without_anchor = link.split('#')[0]
                if link_without_anchor:
                    links.append((link_without_anchor, line_num))
    
    return links


def resolve_link(source_file: Path, link: str) -> Path:
    """解析相对链接的绝对路径"""
    # 如果是相对路径，从源文件目录开始解析
    if link.startswith('./') or link.startswith('../'):
        return (source_file.parent / link).resolve()
    else:
        # 如果是绝对路径（从 docs/ 开始）
        return (source_file.parent / link).resolve()


def check_broken_links(docs_dir: str) -> Dict[str, List[Dict]]:
    """检查损坏的链接"""
    print_header("文档引用检查")
    
    md_files = find_markdown_files(docs_dir)
    print(f"找到 {len(md_files)} 个 Markdown 文件")
    
    broken_links = {}
    total_links = 0
    broken_count = 0
    
    for md_file in md_files:
        try:
            content = md_file.read_text(encoding='utf-8')
        except Exception as e:
            print(f"{YELLOW}警告: 无法读取 {md_file}: {e}{NC}")
            continue
        
        links = extract_links(content, md_file)
        
        for link, line_num in links:
            total_links += 1
            target = resolve_link(md_file, link)
            
            if not target.exists():
                broken_count += 1
                if str(md_file) not in broken_links:
                    broken_links[str(md_file)] = []
                
                broken_links[str(md_file)].append({
                    'link': link,
                    'line': line_num,
                    'target': str(target)
                })
    
    # 打印结果
    print(f"\n检查了 {total_links} 个内部链接")
    
    if broken_links:
        print(f"{RED}发现 {broken_count} 个损坏的链接:{NC}\n")
        
        for source_file, issues in broken_links.items():
            print(f"{RED}文件: {source_file}{NC}")
            for issue in issues:
                print(f"  第 {issue['line']} 行:")
                print(f"    链接: {issue['link']}")
                print(f"    目标: {issue['target']}")
                print()
    else:
        print(f"{GREEN}✓ 所有内部链接都有效！{NC}")
    
    return broken_links


def check_orphaned_files(docs_dir: str) -> List[Path]:
    """检查孤立文件（没有被其他文档引用的文件）"""
    print_header("孤立文件检查")
    
    md_files = find_markdown_files(docs_dir)
    
    # 收集所有被引用的文件
    referenced_files = set()
    
    for md_file in md_files:
        try:
            content = md_file.read_text(encoding='utf-8')
        except:
            continue
        
        links = extract_links(content, md_file)
        
        for link, _ in links:
            target = resolve_link(md_file, link)
            if target.exists():
                referenced_files.add(str(target.resolve()))
    
    # 查找孤立文件（排除 README.md 和 index.md）
    orphaned = []
    for md_file in md_files:
        file_str = str(md_file.resolve())
        file_name = md_file.name.lower()
        
        # 跳过特殊文件
        if file_name in ['readme.md', 'index.md', 'changelog.md']:
            continue
        
        if file_str not in referenced_files:
            orphaned.append(md_file)
    
    if orphaned:
        print(f"{YELLOW}发现 {len(orphaned)} 个孤立文件（未被引用）:{NC}\n")
        for file in sorted(orphaned):
            print(f"  - {file}")
        print(f"\n{YELLOW}建议：确保这些文件被正确引用，或移到 archived/ 目录{NC}")
    else:
        print(f"{GREEN}✓ 所有文档都被引用{NC}")
    
    return orphaned


def main():
    """主函数"""
    # 检查是否在项目根目录
    if not Path('docs').exists():
        print(f"{RED}错误: 请在项目根目录运行此脚本{NC}")
        sys.exit(1)
    
    # 检查损坏的链接
    broken = check_broken_links('docs')
    
    # 检查孤立文件
    orphaned = check_orphaned_files('docs')
    
    # 总结
    print_header("检查总结")
    
    if broken:
        print(f"{RED}✗ 发现损坏的链接{NC}")
        sys.exit(1)
    elif orphaned:
        print(f"{YELLOW}⚠ 发现孤立文件（警告）{NC}")
        sys.exit(0)
    else:
        print(f"{GREEN}✓ 所有检查通过{NC}")
        sys.exit(0)


if __name__ == '__main__':
    main()
