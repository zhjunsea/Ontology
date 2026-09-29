# coding: utf8

import os
from setuptools import setup
from setuptools import find_packages


from towerdesign import __version__

algoritm_lib_name = 'towerdesign'

# 版本号，每次修改代码都需要修改此值才会被用户更新使用
version = __version__

# 需要打包编译的算法文件


setup(
    name=algoritm_lib_name,
    version=version,
    url='http://www.goldwind.com',
    description='cad tower drawing',  # package的简单描述
    author='WU HANG',  # 开发者
    author_email='wuhang@goldwind.com',  # 开发者邮箱
    include_package_data=True,
    zip_safe=False,
    packages=find_packages(),
    install_requires=[  # 依赖的第三方库
        'xlrd==1.2.0',
        'xlwt==1.2.0',
        'openpyxl==3.1.2'
    ],
    classifiers=[
        'Programming Language :: Python',
        'Programming Language :: Python :: 3',
    ],
)
