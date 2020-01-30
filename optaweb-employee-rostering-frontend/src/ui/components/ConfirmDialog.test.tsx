/*
 * Copyright 2019 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import { shallow } from 'enzyme';
import toJson from 'enzyme-to-json';
import * as React from 'react';
import { ConfirmDialogProps, ConfirmDialog } from './ConfirmDialog';

describe('ConfirmDialog component', () => {
  it('should render correctly when closed', () => {
    const confirmDialogComponent = shallow(<ConfirmDialog {...confirmDialogProps} isOpen={false}>Body</ConfirmDialog>);
    expect(toJson(confirmDialogComponent)).toMatchSnapshot();
  });

  it('should render correctly when opened', () => {
    const confirmDialogComponent = shallow(<ConfirmDialog {...confirmDialogProps}>Body</ConfirmDialog>);
    expect(toJson(confirmDialogComponent)).toMatchSnapshot();
  });

  it('should call onClose when close or the cross is clicked', () => {
    const confirmDialogComponent = shallow(<ConfirmDialog {...confirmDialogProps}>Body</ConfirmDialog>);
    confirmDialogComponent.find('Modal[title="Confirm Dialog Title"]').simulate('close');
    expect(confirmDialogProps.onClose).toBeCalled();
    expect(confirmDialogProps.onConfirm).not.toBeCalled();

    jest.resetAllMocks();
    shallow((confirmDialogComponent.find('Modal[title="Confirm Dialog Title"]')
      .prop('actions') as any[])[0]).simulate('click');
    expect(confirmDialogProps.onClose).toBeCalled();
    expect(confirmDialogProps.onConfirm).not.toBeCalled();
  });

  it('should call both onClose and onConfirm when the confirm button is clicked', () => {
    const confirmDialogComponent = shallow(<ConfirmDialog {...confirmDialogProps}>Body</ConfirmDialog>);
    shallow((confirmDialogComponent.find('Modal[title="Confirm Dialog Title"]')
      .prop('actions') as any[])[1]).simulate('click');
    expect(confirmDialogProps.onClose).toBeCalled();
    expect(confirmDialogProps.onConfirm).toBeCalled();
  });
});

const confirmDialogProps: ConfirmDialogProps = {
  title: 'Confirm Dialog Title',
  isOpen: true,
  onConfirm: jest.fn(),
  onClose: jest.fn(),
};
